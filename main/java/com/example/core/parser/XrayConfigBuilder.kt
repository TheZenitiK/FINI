package com.example.core.parser

import com.example.data.local.MincifryWhitelist
import com.example.data.model.AppSettings
import com.example.data.model.SecurityType
import com.example.data.model.ServerConfig
import com.example.data.model.TransportType
import com.example.data.model.VpnProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object XrayConfigBuilder {

    fun getEditableConfigJson(
        server: ServerConfig,
        settings: AppSettings,
        socksPort: Int = 10808
    ): String {
        if (server.customJsonConfig.isNotBlank()) {
            return runCatching { JSONObject(server.customJsonConfig).toString(2) }
                .getOrDefault(server.customJsonConfig)
        }
        return buildConfigJson(server, settings, socksPort)
    }

    fun buildConfigJson(
        server: ServerConfig,
        settings: AppSettings,
        socksPort: Int = 10808,
        datDir: File? = null
    ): String {
        if (server.customJsonConfig.isNotBlank()) {
            buildFromCustomJson(server.customJsonConfig, settings, socksPort, datDir)?.let { return it }
        }

        val root = JSONObject()

        root.put("log", JSONObject().apply {
            put("loglevel", if (settings.logsEnabled) "info" else "warning")
        })

        val dnsServers = JSONArray().apply {
            put("1.1.1.1")
            put("8.8.8.8")
            if (settings.bypassRussianDomains || settings.bypassMincifry) {
                put(JSONObject().apply {
                    put("address", "77.88.8.8")
                    put("domains", JSONArray().apply {
                        put("regexp:.*\\.ru$")
                        put("regexp:.*\\.xn--p1ai$")
                    })
                })
            }
        }
        root.put("dns", JSONObject().apply {
            put("servers", dnsServers)
        })

        root.put("inbounds", JSONArray().apply {
            put(buildSocksInbound(socksPort))
        })

        val outbounds = JSONArray().apply {
            put(buildProxyOutbound(server))
            put(buildDirectOutbound())
            put(buildBlockOutbound())
        }
        root.put("outbounds", outbounds)

        val rules = buildBypassRules(settings)
        root.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", rules)
        })

        return root.toString(2)
    }

    private fun buildFromCustomJson(
        rawCustomJson: String,
        settings: AppSettings,
        socksPort: Int,
        datDir: File?
    ): String? {
        return runCatching {
            val root = JSONObject(rawCustomJson)
            root.remove("remarks")
            root.remove("meta")

            val hasGeoIpDat = datDir?.let { File(it, "geoip.dat").isFile } == true
            val hasGeoSiteDat = datDir?.let { File(it, "geosite.dat").isFile } == true

            val logObj = root.optJSONObject("log") ?: JSONObject()
            if (!logObj.has("loglevel")) {
                logObj.put("loglevel", if (settings.logsEnabled) "info" else "warning")
            }
            root.put("log", logObj)

            // Sanitize DNS server filters that reference geoip:/geosite: when .dat assets are absent
            root.optJSONObject("dns")?.let { dnsObj ->
                sanitizeDnsServers(dnsObj, hasGeoIpDat, hasGeoSiteDat)
            }

            // Replace local tunnel/proxy inbounds with FINI's ephemeral socks-in port
            val existingInbounds = root.optJSONArray("inbounds")
            val newInbounds = JSONArray().apply {
                put(buildSocksInbound(socksPort))
            }
            if (existingInbounds != null) {
                for (i in 0 until existingInbounds.length()) {
                    val ib = existingInbounds.optJSONObject(i) ?: continue
                    val proto = ib.optString("protocol").lowercase()
                    val tag = ib.optString("tag").lowercase()
                    if (tag != "socks-in" && proto !in setOf("socks", "http", "tun", "dokodemo-door")) {
                        newInbounds.put(ib)
                    }
                }
            }
            root.put("inbounds", newInbounds)

            // Ensure direct & block outbounds exist without altering primary outbound ordering
            val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
            var hasDirect = false
            var hasBlock = false
            for (i in 0 until outbounds.length()) {
                val ob = outbounds.optJSONObject(i) ?: continue
                when (ob.optString("tag").lowercase()) {
                    "direct" -> hasDirect = true
                    "block" -> hasBlock = true
                }
            }
            if (!hasDirect) outbounds.put(buildDirectOutbound())
            if (!hasBlock) outbounds.put(buildBlockOutbound())
            root.put("outbounds", outbounds)

            // Prepend FINI bypass rules while preserving existing balancers & sanitized rules
            val bypassRules = buildBypassRules(settings)
            val routingObj = root.optJSONObject("routing") ?: JSONObject()
            if (!routingObj.has("domainStrategy")) {
                routingObj.put("domainStrategy", "IPIfNonMatch")
            }
            val mergedRules = JSONArray()
            for (i in 0 until bypassRules.length()) {
                mergedRules.put(bypassRules.get(i))
            }
            val existingRules = routingObj.optJSONArray("rules")
            if (existingRules != null) {
                for (i in 0 until existingRules.length()) {
                    val rule = existingRules.optJSONObject(i) ?: continue
                    sanitizeRoutingRule(rule, hasGeoIpDat, hasGeoSiteDat)?.let { cleanRule ->
                        mergedRules.put(cleanRule)
                    }
                }
            }
            routingObj.put("rules", mergedRules)
            root.put("routing", routingObj)

            root.toString(2)
        }.getOrNull()
    }

    private fun sanitizeIpArray(
        arr: JSONArray,
        hasGeoIpDat: Boolean
    ): JSONArray {
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i).trim()
            if (item.isEmpty()) continue
            if (item.equals("geoip:private", ignoreCase = true)) {
                MincifryWhitelist.privateLanCidrs.forEach { out.put(it) }
            } else if (item.lowercase().startsWith("geoip:") && !hasGeoIpDat) {
                continue
            } else {
                out.put(item)
            }
        }
        return out
    }

    private fun sanitizeDomainArray(
        arr: JSONArray,
        hasGeoSiteDat: Boolean
    ): JSONArray {
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i).trim()
            if (item.isEmpty()) continue
            if (item.lowercase().startsWith("geosite:") && !hasGeoSiteDat) {
                continue
            }
            out.put(item)
        }
        return out
    }

    private fun sanitizeRoutingRule(
        rule: JSONObject,
        hasGeoIpDat: Boolean,
        hasGeoSiteDat: Boolean
    ): JSONObject? {
        val ipArr = rule.optJSONArray("ip")
        if (ipArr != null) {
            val cleaned = sanitizeIpArray(ipArr, hasGeoIpDat)
            if (ipArr.length() > 0 && cleaned.length() == 0) return null
            rule.put("ip", cleaned)
        }
        val sourceArr = rule.optJSONArray("source")
        if (sourceArr != null) {
            val cleaned = sanitizeIpArray(sourceArr, hasGeoIpDat)
            if (sourceArr.length() > 0 && cleaned.length() == 0) return null
            rule.put("source", cleaned)
        }
        for (domainKey in listOf("domain", "domains")) {
            val domArr = rule.optJSONArray(domainKey)
            if (domArr != null) {
                val cleaned = sanitizeDomainArray(domArr, hasGeoSiteDat)
                if (domArr.length() > 0 && cleaned.length() == 0) return null
                rule.put(domainKey, cleaned)
            }
        }
        return rule
    }

    private fun sanitizeDnsServers(
        dnsObj: JSONObject,
        hasGeoIpDat: Boolean,
        hasGeoSiteDat: Boolean
    ) {
        val servers = dnsObj.optJSONArray("servers") ?: return
        val kept = JSONArray()
        for (i in 0 until servers.length()) {
            val entry = servers.opt(i)
            if (entry !is JSONObject) {
                kept.put(entry)
                continue
            }
            var dropScopedServer = false
            entry.optJSONArray("domains")?.let { domArr ->
                val cleaned = sanitizeDomainArray(domArr, hasGeoSiteDat)
                if (domArr.length() > 0 && cleaned.length() == 0) {
                    dropScopedServer = true
                } else {
                    entry.put("domains", cleaned)
                }
            }
            if (dropScopedServer) continue

            entry.optJSONArray("expectIPs")?.let { expArr ->
                val cleaned = sanitizeIpArray(expArr, hasGeoIpDat)
                if (cleaned.length() == 0) {
                    entry.remove("expectIPs")
                } else {
                    entry.put("expectIPs", cleaned)
                }
            }
            kept.put(entry)
        }
        dnsObj.put("servers", kept)
    }

    private fun buildSocksInbound(socksPort: Int): JSONObject = JSONObject().apply {
        put("tag", "socks-in")
        put("port", socksPort)
        put("listen", "127.0.0.1")
        put("protocol", "socks")
        put("settings", JSONObject().apply {
            put("auth", "noauth")
            put("udp", true)
            put("ip", "127.0.0.1")
        })
        put("sniffing", JSONObject().apply {
            put("enabled", true)
            put("destOverride", JSONArray().apply {
                put("http")
                put("tls")
                put("quic")
            })
            put("routeOnly", true)
        })
    }

    private fun buildDirectOutbound(): JSONObject = JSONObject().apply {
        put("tag", "direct")
        put("protocol", "freedom")
        put("settings", JSONObject().apply {
            put("domainStrategy", "UseIP")
        })
    }

    private fun buildBlockOutbound(): JSONObject = JSONObject().apply {
        put("tag", "block")
        put("protocol", "blackhole")
    }

    private fun buildBypassRules(settings: AppSettings): JSONArray {
        val rules = JSONArray()

        if (settings.bypassLan) {
            rules.put(JSONObject().apply {
                put("type", "field")
                put("ip", JSONArray().apply {
                    MincifryWhitelist.privateLanCidrs.forEach { put(it) }
                })
                put("outboundTag", "direct")
            })
        }

        if (settings.bypassRussianDomains) {
            rules.put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray().apply {
                    put("regexp:.*\\.ru$")
                    put("regexp:.*\\.su$")
                    put("regexp:.*\\.xn--p1ai$")
                })
                put("outboundTag", "direct")
            })
        }

        if (settings.bypassMincifry) {
            val domains = MincifryWhitelist.defaultDomains
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
            if (domains.isNotEmpty()) {
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().apply {
                        domains.forEach { put("domain:$it") }
                    })
                    put("outboundTag", "direct")
                })
            }
        }

        if (settings.customRoutesEnabled && settings.customDirectDomains.isNotEmpty()) {
            val customDomains = settings.customDirectDomains
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
            if (customDomains.isNotEmpty()) {
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().apply {
                        customDomains.forEach { put("domain:$it") }
                    })
                    put("outboundTag", "direct")
                })
            }
        }

        return rules
    }

    private fun buildProxyOutbound(server: ServerConfig): JSONObject {
        val outbound = JSONObject().apply {
            put("tag", "proxy")
        }

        when (server.protocol) {
            VpnProtocol.VLESS -> {
                outbound.put("protocol", "vless")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", server.host)
                            put("port", server.port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", server.uuidOrPassword)
                                    put("encryption", server.encryption.ifBlank { "none" })
                                    if (server.flow.isNotBlank()) {
                                        put("flow", server.flow)
                                    }
                                })
                            })
                        })
                    })
                })
            }
            VpnProtocol.VMESS -> {
                outbound.put("protocol", "vmess")
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", server.host)
                            put("port", server.port)
                            put("users", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("id", server.uuidOrPassword)
                                    put("alterId", 0)
                                    put("security", "auto")
                                })
                            })
                        })
                    })
                })
            }
            VpnProtocol.TROJAN -> {
                outbound.put("protocol", "trojan")
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", server.host)
                            put("port", server.port)
                            put("password", server.uuidOrPassword)
                        })
                    })
                })
            }
            VpnProtocol.SHADOWSOCKS -> {
                outbound.put("protocol", "shadowsocks")
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("address", server.host)
                            put("port", server.port)
                            put("method", server.encryption.ifBlank { "aes-256-gcm" })
                            put("password", server.uuidOrPassword)
                        })
                    })
                })
            }
            VpnProtocol.HYSTERIA2 -> {
                outbound.put("protocol", "hysteria")
                outbound.put("settings", JSONObject().apply {
                    put("version", 2)
                    put("address", server.host)
                    put("port", server.port)
                })
            }
        }

        outbound.put("streamSettings", buildStreamSettings(server))
        return outbound
    }

    private fun buildStreamSettings(server: ServerConfig): JSONObject {
        val stream = JSONObject()
        if (server.protocol == VpnProtocol.HYSTERIA2) {
            stream.put("network", "hysteria")
            stream.put("security", "tls")

            stream.put("tlsSettings", JSONObject().apply {
                put("serverName", server.sni.ifBlank { server.host })
                put("allowInsecure", server.allowInsecure)
                val alpnList = server.alpn.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { listOf("h3") }
                put("alpn", JSONArray().apply { alpnList.forEach { put(it) } })
                if (server.pinSha256.isNotBlank()) {
                    put("pinnedPeerCertSha256", server.pinSha256)
                }
            })

            stream.put("hysteriaSettings", JSONObject().apply {
                put("version", 2)
                put("auth", server.uuidOrPassword)
                if (server.upMbps > 0) put("up", "${server.upMbps} mbps")
                if (server.downMbps > 0) put("down", "${server.downMbps} mbps")
            })

            val finalMask = JSONObject()
            if (server.obfs.equals("salamander", ignoreCase = true) && server.obfsPassword.isNotBlank()) {
                finalMask.put("udp", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "salamander")
                        put("settings", JSONObject().apply {
                            put("password", server.obfsPassword)
                        })
                    })
                })
            }
            if (server.upMbps > 0 || server.downMbps > 0 || server.ports.isNotBlank()) {
                finalMask.put("quicParams", JSONObject().apply {
                    if (server.upMbps > 0 || server.downMbps > 0) {
                        put("congestion", "brutal")
                        if (server.upMbps > 0) put("brutalUp", "${server.upMbps}mbps")
                        if (server.downMbps > 0) put("brutalDown", "${server.downMbps}mbps")
                    }
                    if (server.ports.isNotBlank()) {
                        put("udpHop", JSONObject().apply {
                            put("ports", server.ports)
                            put("interval", "30")
                        })
                    }
                })
            }
            if (finalMask.length() > 0) {
                stream.put("finalmask", finalMask)
            }
            return stream
        }

        stream.put("network", server.transport.raw)
        stream.put("security", server.security.raw)

        when (server.security) {
            SecurityType.REALITY -> {
                stream.put("realitySettings", JSONObject().apply {
                    put("show", false)
                    put("fingerprint", server.fingerprint.ifBlank { "chrome" })
                    put("serverName", server.sni.ifBlank { server.host })
                    put("publicKey", server.publicKey)
                    put("shortId", server.shortId)
                    put("spiderX", server.spiderX)
                })
            }
            SecurityType.TLS -> {
                stream.put("tlsSettings", JSONObject().apply {
                    put("serverName", server.sni.ifBlank { server.host })
                    put("fingerprint", server.fingerprint.ifBlank { "chrome" })
                    put("allowInsecure", server.allowInsecure)
                    if (server.alpn.isNotBlank()) {
                        put("alpn", JSONArray().apply {
                            server.alpn.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                                put(it)
                            }
                        })
                    }
                })
            }
            SecurityType.NONE -> Unit
        }

        when (server.transport) {
            TransportType.XHTTP -> {
                stream.put("xhttpSettings", JSONObject().apply {
                    put("path", server.path.ifBlank { "/" })
                    if (server.hostHeader.isNotBlank()) put("host", server.hostHeader)
                    put("mode", server.xhttpMode.ifBlank { "auto" })
                })
            }
            TransportType.WS -> {
                stream.put("wsSettings", JSONObject().apply {
                    put("path", server.path.ifBlank { "/" })
                    if (server.hostHeader.isNotBlank()) {
                        put("headers", JSONObject().apply {
                            put("Host", server.hostHeader)
                        })
                    }
                })
            }
            TransportType.GRPC -> {
                val rawService = server.path.trim()
                val cleanServiceName = if (rawService == "/") "" else rawService.removePrefix("/")
                stream.put("grpcSettings", JSONObject().apply {
                    put("serviceName", cleanServiceName)
                    if (server.xhttpMode.equals("multi", ignoreCase = true)) {
                        put("multiMode", true)
                    }
                    if (server.hostHeader.isNotBlank()) {
                        put("authority", server.hostHeader)
                    }
                    put("idle_timeout", 60)
                    put("health_check_timeout", 20)
                    put("permit_without_stream", false)
                    put("initial_windows_size", 0)
                })
            }
            TransportType.HTTPUPGRADE -> {
                stream.put("httpupgradeSettings", JSONObject().apply {
                    put("path", server.path.ifBlank { "/" })
                    if (server.hostHeader.isNotBlank()) put("host", server.hostHeader)
                })
            }
            TransportType.TCP -> Unit
        }

        return stream
    }
}
