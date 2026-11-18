package com.example.core.network

import com.example.data.model.PingMethod
import com.example.data.model.ServerConfig
import com.example.data.model.VpnProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

object PingEngine {

    private val httpClient: OkHttpClient by lazy {
        SharedHttpClient.baseClient.newBuilder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .build()
    }

    /**
     * Measures server latency in milliseconds according to the selected [PingMethod].
     * Returns null if the server did not respond or timed out.
     */
    suspend fun measurePing(server: ServerConfig, method: PingMethod): Int? =
        withContext(Dispatchers.IO) {
            if (server.protocol == VpnProtocol.HYSTERIA2) {
                return@withContext when (method) {
                    PingMethod.ICMP -> measureIcmpPing(server.host)
                    PingMethod.TCP, PingMethod.HTTP_GET -> {
                        if (!server.obfs.equals("salamander", ignoreCase = true)) {
                            measureQuicUdpPing(server.host, server.port)
                                ?: measureTcpPing(server.host, server.port)
                                ?: measureIcmpPing(server.host)
                        } else {
                            measureTcpPing(server.host, server.port)
                                ?: measureIcmpPing(server.host)
                        }
                    }
                }
            }
            when (method) {
                PingMethod.TCP -> measureTcpPing(server.host, server.port)
                PingMethod.HTTP_GET -> measureHttpGetPing(server.host, server.port)
                PingMethod.ICMP -> measureIcmpPing(server.host)
            }
        }

    private fun measureQuicUdpPing(host: String, port: Int): Int? {
        return runCatching {
            val address = InetAddress.getByName(host)
            // RFC 9000 QUIC Long Header (1200 bytes) with reserved version 0x0A0A0A0A
            // prompts a stateless Version Negotiation reply from QUIC / Hysteria 2 endpoints.
            val probe = ByteArray(1200)
            probe[0] = 0xC0.toByte()
            probe[1] = 0x0A
            probe[2] = 0x0A
            probe[3] = 0x0A
            probe[4] = 0x0A
            probe[5] = 8 // DCID len
            for (i in 6 until 14) probe[i] = (i * 17).toByte()
            probe[14] = 8 // SCID len
            for (i in 15 until 23) probe[i] = (i * 31).toByte()

            val recvBuf = ByteArray(1500)
            DatagramSocket().use { socket ->
                socket.soTimeout = 2500
                val startNs = System.nanoTime()
                socket.send(DatagramPacket(probe, probe.size, address, port))
                socket.receive(DatagramPacket(recvBuf, recvBuf.size))
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt()
                elapsedMs.coerceAtLeast(1)
            }
        }.getOrNull()
    }

    private fun measureTcpPing(host: String, port: Int): Int? {
        return runCatching {
            val startNs = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
            }
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt()
            elapsedMs.coerceAtLeast(1)
        }.getOrNull()
    }

    private fun measureHttpGetPing(host: String, port: Int): Int? {
        // Measure HTTP/HTTPS GET latency directly to the host (fallback to HTTP probe if TLS handshake rejects plain HTTP)
        val scheme = if (port == 80 || port == 8080) "http" else "https"
        val url = "$scheme://$host:$port/"
        val startNs = System.nanoTime()
        val directResult = runCatching {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { _ ->
                ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            }
        }.getOrNull()

        if (directResult != null) return directResult

        // Many Reality/VLESS nodes reject direct HTTP GET without valid SNI/TLS; measure TCP + HTTP probe fallback
        return runCatching {
            val probeStart = System.nanoTime()
            Socket().use { socket ->
                socket.soTimeout = 2500
                socket.connect(InetSocketAddress(host, port), 2500)
                val out = socket.getOutputStream()
                out.write("GET / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                socket.getInputStream().read()
            }
            ((System.nanoTime() - probeStart) / 1_000_000L).toInt().coerceAtLeast(1)
        }.getOrNull()
    }

    private fun measureIcmpPing(host: String): Int? {
        // First try /system/bin/ping -c 1 -W 2 for accurate ICMP RTT
        val cmdResult = runCatching {
            val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "2", host)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exit = process.waitFor()
            if (exit == 0) {
                val match = Regex("""time[=<](\d+(?:\.\d+)?)\s*ms""").find(output)
                match?.groupValues?.getOrNull(1)?.toFloatOrNull()?.toInt()?.coerceAtLeast(1)
            } else null
        }.getOrNull()

        if (cmdResult != null) return cmdResult

        // Fallback to InetAddress.isReachable
        return runCatching {
            val addr = InetAddress.getByName(host)
            val startNs = System.nanoTime()
            if (addr.isReachable(2000)) {
                ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
            } else null
        }.getOrNull()
    }
}
