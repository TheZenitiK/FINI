package com.example.core.parser

import android.util.Base64
import com.example.data.model.SecurityType
import com.example.data.model.ServerConfig
import com.example.data.model.TransportType
import com.example.data.model.VpnProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID

sealed class ParsedImportResult {
    data class SingleOrList(val servers: List<ServerConfig>) : ParsedImportResult()
    data class SubscriptionUrl(val url: String) : ParsedImportResult()
    data class Error(val message: String) : ParsedImportResult()
}

object VlessUriParser {

    fun unwrapSubscriptionDeepLink(rawInput: String): String {
        val cleaned = rawInput
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            .trim()
        val lower = cleaned.lowercase()

        val embeddedHttpIdx = Regex("""https?://""", RegexOption.IGNORE_CASE).find(cleaned)?.range?.first
        val isCustomDeepLink = lower.startsWith("happ://") ||
            lower.startsWith("v2raytun://") ||
            lower.startsWith("hiddify://") ||
            lower.startsWith("streisand://") ||
            lower.startsWith("v2box://") ||
            lower.startsWith("foxray://") ||
            lower.startsWith("clash://") ||
            lower.startsWith("sn://")

        if (isCustomDeepLink) {
            val queryUrlMatch = Regex("""(?:[?&]url=)([^&\s]+)""", RegexOption.IGNORE_CASE).find(cleaned)
            if (queryUrlMatch != null) {
                val rawParam = queryUrlMatch.groupValues[1]
                val decoded = runCatching {
                    URLDecoder.decode(rawParam.replace("+", "%2B"), "UTF-8")
                }.getOrDefault(rawParam).trim()
                if (decoded.startsWith("http://", ignoreCase = true) ||
                    decoded.startsWith("https://", ignoreCase = true)
                ) {
                    return decoded
                }
            }
            if (embeddedHttpIdx != null && embeddedHttpIdx > 0) {
                return cleaned.substring(embeddedHttpIdx).trim()
            }
            val encodedHttpIdx = Regex("""https?%3A%2F%2F""", RegexOption.IGNORE_CASE).find(cleaned)?.range?.first
            if (encodedHttpIdx != null && encodedHttpIdx > 0) {
                val slice = cleaned.substring(encodedHttpIdx)
                val decoded = runCatching {
                    URLDecoder.decode(slice.replace("+", "%2B"), "UTF-8")
                }.getOrDefault(slice).trim()
                if (decoded.startsWith("http://", ignoreCase = true) ||
                    decoded.startsWith("https://", ignoreCase = true)
                ) {
                    return decoded
                }
            }
        }
        return cleaned
    }

    fun parseClipboardOrQrInput(rawInput: String, subscriptionId: String? = null): ParsedImportResult {
        val trimmed = unwrapSubscriptionDeepLink(rawInput)
        if (trimmed.isEmpty()) {
            return ParsedImportResult.Error("Буфер обмена пуст")
        }

        if ((trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)) &&
            !trimmed.contains("\n") &&
            !trimmed.contains(" ")
        ) {
            return ParsedImportResult.SubscriptionUrl(trimmed)
        }

        val servers = parseSubscriptionBody(trimmed, subscriptionId)
        if (servers.isNotEmpty()) {
            return ParsedImportResult.SingleOrList(servers)
        }

        return ParsedImportResult.Error(
            "Не найдено поддерживаемых конфигураций VLESS / VMess / Trojan / Shadowsocks / Hysteria 2, JSON или URL подписки"
        )
    }

    /**
     * Parses any subscription payload: plain URI lines, Base64-encoded lines,
     * Xray/Sing-box JSON config arrays, or Clash YAML proxy blocks.
     * Preserves exact source order via [ServerConfig.sortOrder].
     */
    fun parseSubscriptionBody(body: String, subscriptionId: String? = null): List<ServerConfig> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 0. Clash YAML format when payload is YAML (not raw JSON)
        val looksLikeClashYaml = !trimmed.startsWith("{") &&
            !trimmed.startsWith("[") &&
            Regex("""(?m)^\s*proxies\s*:""").containsMatchIn(trimmed)
        if (looksLikeClashYaml) {
            val fromYaml = parseClashYaml(trimmed, subscriptionId)
            if (fromYaml.isNotEmpty()) return deduplicateServers(fromYaml)
        }

        // 1. Mixed payload: supports URI lines, single/concatenated JSON objects, JSON arrays, and mixes of both in source order
        val mixedDirect = parseMixedPayload(trimmed, subscriptionId)
        if (mixedDirect.isNotEmpty()) return deduplicateServers(mixedDirect)

        // 2. Base64-decoded payload (common for Marzban / 3x-ui / Remnawave / Xray subscriptions)
        val decoded = tryDecodeBase64(trimmed)
        if (decoded != null) {
            val decTrimmed = decoded.trim()
            val decLooksLikeYaml = !decTrimmed.startsWith("{") &&
                !decTrimmed.startsWith("[") &&
                Regex("""(?m)^\s*proxies\s*:""").containsMatchIn(decTrimmed)
            if (decLooksLikeYaml) {
                val fromDecodedYaml = parseClashYaml(decTrimmed, subscriptionId)
                if (fromDecodedYaml.isNotEmpty()) return deduplicateServers(fromDecodedYaml)
            }

            val fromDecodedMixed = parseMixedPayload(decTrimmed, subscriptionId)
            if (fromDecodedMixed.isNotEmpty()) return deduplicateServers(fromDecodedMixed)
        }

        // 3. Clash YAML fallback (in case proxies: was indented or preceded by other blocks)
        if (trimmed.contains("proxies:", ignoreCase = true)) {
            val fromYaml = parseClashYaml(trimmed, subscriptionId)
            if (fromYaml.isNotEmpty()) return deduplicateServers(fromYaml)
        }

        // 4. Fallback: scan arbitrary text for embedded proxy URI tokens
        val embeddedRegex = Regex(
            """(?:vless|trojan|vmess|ss|hysteria2|hy2)://[^\s"'<>]+""",
            RegexOption.IGNORE_CASE
        )
        val embedded = embeddedRegex.findAll(trimmed).mapIndexedNotNull { idx, match ->
            parseSingleUri(match.value, subscriptionId, idx)
        }.toList()
        if (embedded.isNotEmpty()) return deduplicateServers(embedded)

        return emptyList()
    }

    /**
     * Walks a mixed text payload and extracts balanced top-level JSON objects/arrays
     * as well as interleaved proxy URI lines while preserving exact source order.
     */
    private fun parseMixedPayload(text: String, subscriptionId: String?): List<ServerConfig> {
        val result = mutableListOf<ServerConfig>()
        val len = text.length
        var segmentStart = 0
        var i = 0

        while (i < len) {
            val ch = text[i]
            val atJsonBoundary = isTopLevelJsonStart(text, i)
            if ((ch == '{' || ch == '[') && atJsonBoundary) {
                val closeIdx = findBalancedJsonEnd(text, i)
                if (closeIdx > i) {
                    val before = text.substring(segmentStart, i)
                    if (before.isNotBlank()) {
                        result.addAll(parseMultipleLines(before, subscriptionId))
                    }
                    val jsonCandidate = text.substring(i, closeIdx + 1)
                    val parsedJson = parseJsonConfigs(jsonCandidate, subscriptionId)
                    if (parsedJson.isNotEmpty()) {
                        result.addAll(parsedJson)
                    } else {
                        result.addAll(parseMultipleLines(jsonCandidate, subscriptionId))
                    }
                    i = closeIdx + 1
                    segmentStart = i
                    continue
                }
            }
            i++
        }

        if (segmentStart < len) {
            val tail = text.substring(segmentStart)
            if (tail.isNotBlank()) {
                result.addAll(parseMultipleLines(tail, subscriptionId))
            }
        }
        return result.mapIndexed { idx, srv -> srv.copy(sortOrder = idx) }
    }

    private fun isTopLevelJsonStart(text: String, index: Int): Boolean {
        if (index == 0) return true
        // Look back to start of current line: allow JSON only if preceded by whitespace/commas or immediately after '}' / ']'
        var k = index - 1
        while (k >= 0) {
            val c = text[k]
            if (c == '\n' || c == '\r' || c == '}' || c == ']') return true
            if (!c.isWhitespace() && c != ',') return false
            k--
        }
        return true
    }

    private fun findBalancedJsonEnd(text: String, startIndex: Int): Int {
        val openChar = text[startIndex]
        val closeChar = if (openChar == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        var i = startIndex
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    openChar -> depth++
                    closeChar -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun extractTopLevelJsonBlocks(rawText: String): List<String> {
        val blocks = mutableListOf<String>()
        var i = 0
        while (i < rawText.length) {
            val c = rawText[i]
            if ((c == '{' || c == '[') && isTopLevelJsonStart(rawText, i)) {
                val end = findBalancedJsonEnd(rawText, i)
                if (end > i) {
                    blocks.add(rawText.substring(i, end + 1))
                    i = end + 1
                    continue
                }
            }
            i++
        }
        return blocks
    }

    private fun deduplicateServers(servers: List<ServerConfig>): List<ServerConfig> {
        val seenKeys = LinkedHashSet<String>()
        val deduped = ArrayList<ServerConfig>(servers.size)
        servers.forEach { srv ->
            val canonicalKey = when {
                srv.customJsonConfig.isNotBlank() -> "JSON|${srv.customJsonConfig.trim()}"
                srv.isAutoSelect -> "AUTO|${srv.name.trim().lowercase()}"
                else -> listOf(
                    srv.protocol.name,
                    srv.host.trim().lowercase(),
                    srv.port.toString(),
                    srv.uuidOrPassword.trim(),
                    srv.transport.raw,
                    srv.security.raw,
                    srv.sni.trim().lowercase(),
                    srv.publicKey.trim(),
                    srv.shortId.trim(),
                    srv.hostHeader.trim().lowercase(),
                    srv.path.trim(),
                    srv.obfs.trim().lowercase(),
                    srv.obfsPassword.trim()
                ).joinToString("|")
            }
            if (seenKeys.add(canonicalKey)) {
                deduped.add(srv.copy(sortOrder = deduped.size))
            }
        }
        return deduped
    }

    fun parseMultipleLines(text: String, subscriptionId: String? = null): List<ServerConfig> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
        val result = mutableListOf<ServerConfig>()
        lines.forEach { line ->
            parseSingleUri(line, subscriptionId, result.size)?.let { result.add(it) }
        }
        return result
    }

    fun parseSingleUri(uriString: String, subscriptionId: String? = null, sortOrder: Int = 0): ServerConfig? {
        val trimmed = uriString.trim()
        return when {
            trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed, subscriptionId, sortOrder)
            trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed, subscriptionId, sortOrder)
            trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed, subscriptionId, sortOrder)
            trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed, subscriptionId, sortOrder)
            trimmed.startsWith("hysteria2://", ignoreCase = true) ||
                trimmed.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(trimmed, subscriptionId, sortOrder)
            else -> null
        }
    }

    /**
     * Tolerant RFC-relaxed URI splitter that never throws URISyntaxException
     * on unencoded spaces, Cyrillic characters, '|' or emojis inside fragments/remarks.
     */
    private data class SplitUri(
        val userInfo: String,
        val host: String,
        val port: Int,
        val rawQuery: String?,
        val rawFragment: String?
    )

    private fun splitAuthorityUri(rawUri: String, defaultPort: Int): SplitUri? {
        val schemeIdx = rawUri.indexOf("://")
        if (schemeIdx < 0) return null
        val afterScheme = rawUri.substring(schemeIdx + 3)

        val hashIdx = afterScheme.indexOf('#')
        val beforeFragment = if (hashIdx >= 0) afterScheme.substring(0, hashIdx) else afterScheme
        val fragment = if (hashIdx >= 0) afterScheme.substring(hashIdx + 1) else null

        val queryIdx = beforeFragment.indexOf('?')
        val authorityAndPath = if (queryIdx >= 0) beforeFragment.substring(0, queryIdx) else beforeFragment
        val query = if (queryIdx >= 0) beforeFragment.substring(queryIdx + 1) else null

        // Strip trailing slash/path after authority if any
        val authority = authorityAndPath.substringBefore('/')
        val atIdx = authority.lastIndexOf('@')
        if (atIdx <= 0) return null

        val userInfo = decodeUrl(authority.substring(0, atIdx))
        val hostPort = authority.substring(atIdx + 1).trim()
        if (hostPort.isEmpty()) return null

        val (host, port) = if (hostPort.startsWith("[")) {
            val closeBracket = hostPort.indexOf(']')
            if (closeBracket < 0) return null
            val h = hostPort.substring(1, closeBracket)
            val portStr = hostPort.substring(closeBracket + 1).removePrefix(":")
            h to (portStr.toIntOrNull() ?: defaultPort)
        } else {
            val colonIdx = hostPort.lastIndexOf(':')
            if (colonIdx > 0) {
                val h = hostPort.substring(0, colonIdx)
                val p = hostPort.substring(colonIdx + 1).toIntOrNull() ?: defaultPort
                h to p
            } else {
                hostPort to defaultPort
            }
        }

        if (host.isBlank()) return null
        return SplitUri(userInfo, host, port, query, fragment)
    }

    private fun parseVless(rawUri: String, subscriptionId: String?, sortOrder: Int): ServerConfig? {
        return runCatching {
            val split = splitAuthorityUri(rawUri, 443) ?: return null
            val uuid = split.userInfo.takeIf { it.isNotBlank() } ?: return null
            val params = parseQueryParams(split.rawQuery)

            val remark = split.rawFragment?.let { decodeUrl(it) }?.takeIf { it.isNotBlank() }
                ?: "${split.host}:${split.port}"

            val typeRaw = params["type"]?.lowercase() ?: "tcp"
            val transport = when (typeRaw) {
                "xhttp", "splithttp" -> TransportType.XHTTP
                "ws", "websocket" -> TransportType.WS
                "grpc" -> TransportType.GRPC
                "httpupgrade" -> TransportType.HTTPUPGRADE
                else -> TransportType.TCP
            }

            val securityRaw = params["security"]?.lowercase() ?: "none"
            val security = when (securityRaw) {
                "reality" -> SecurityType.REALITY
                "tls", "xtls" -> SecurityType.TLS
                else -> SecurityType.NONE
            }

            val isAuto = detectAutoSelectMarker(remark, split.host, split.port)
            val (parsedName, parsedNote) = extractServerNameAndNote(remark)

            val serviceOrPath = if (transport == TransportType.GRPC) {
                params["serviceName"]?.takeIf { it.isNotBlank() } ?: params["path"] ?: ""
            } else {
                params["path"] ?: params["serviceName"] ?: "/"
            }
            val modeParam = params["mode"] ?: if (transport == TransportType.GRPC) "gun" else "auto"
            val hostOrAuthority = if (transport == TransportType.GRPC) {
                params["authority"]?.takeIf { it.isNotBlank() } ?: params["host"] ?: ""
            } else {
                params["host"] ?: ""
            }

            ServerConfig(
                id = UUID.randomUUID().toString(),
                name = parsedName,
                note = parsedNote,
                countryFlag = if (isAuto) "⚡" else detectCountryFlag(remark, split.host),
                protocol = VpnProtocol.VLESS,
                transport = transport,
                security = security,
                host = split.host,
                port = split.port,
                uuidOrPassword = uuid,
                flow = params["flow"] ?: "",
                encryption = params["encryption"] ?: "none",
                sni = params["sni"] ?: params["peer"] ?: "",
                fingerprint = params["fp"] ?: "chrome",
                publicKey = params["pbk"] ?: "",
                shortId = params["sid"] ?: "",
                spiderX = params["spx"] ?: "",
                path = serviceOrPath,
                hostHeader = hostOrAuthority,
                xhttpMode = modeParam,
                alpn = params["alpn"] ?: "",
                allowInsecure = isTrueParam(params["allowInsecure"]) || isTrueParam(params["insecure"]),
                rawUri = rawUri,
                subscriptionId = subscriptionId,
                isAutoSelect = isAuto,
                sortOrder = sortOrder
            )
        }.getOrNull()
    }

    private fun parseTrojan(rawUri: String, subscriptionId: String?, sortOrder: Int): ServerConfig? {
        return runCatching {
            val split = splitAuthorityUri(rawUri, 443) ?: return null
            val password = split.userInfo.takeIf { it.isNotBlank() } ?: return null
            val params = parseQueryParams(split.rawQuery)
            val remark = split.rawFragment?.let { decodeUrl(it) }?.takeIf { it.isNotBlank() }
                ?: "${split.host}:${split.port}"
            val typeRaw = params["type"]?.lowercase() ?: "tcp"
            val transport = when (typeRaw) {
                "xhttp", "splithttp" -> TransportType.XHTTP
                "ws", "websocket" -> TransportType.WS
                "grpc" -> TransportType.GRPC
                "httpupgrade" -> TransportType.HTTPUPGRADE
                else -> TransportType.TCP
            }
            val security = when (params["security"]?.lowercase()) {
                "reality" -> SecurityType.REALITY
                "none" -> SecurityType.NONE
                else -> SecurityType.TLS
            }
            val isAuto = detectAutoSelectMarker(remark, split.host, split.port)
            val (parsedName, parsedNote) = extractServerNameAndNote(remark)
            val serviceOrPath = if (transport == TransportType.GRPC) {
                params["serviceName"]?.takeIf { it.isNotBlank() } ?: params["path"] ?: ""
            } else {
                params["path"] ?: params["serviceName"] ?: "/"
            }
            val modeParam = params["mode"] ?: if (transport == TransportType.GRPC) "gun" else "auto"
            val hostOrAuthority = if (transport == TransportType.GRPC) {
                params["authority"]?.takeIf { it.isNotBlank() } ?: params["host"] ?: ""
            } else {
                params["host"] ?: ""
            }

            ServerConfig(
                id = UUID.randomUUID().toString(),
                name = parsedName,
                note = parsedNote,
                countryFlag = if (isAuto) "⚡" else detectCountryFlag(remark, split.host),
                protocol = VpnProtocol.TROJAN,
                transport = transport,
                security = security,
                host = split.host,
                port = split.port,
                uuidOrPassword = password,
                sni = params["sni"] ?: params["peer"] ?: split.host,
                fingerprint = params["fp"] ?: "chrome",
                publicKey = params["pbk"] ?: "",
                shortId = params["sid"] ?: "",
                path = serviceOrPath,
                hostHeader = hostOrAuthority,
                xhttpMode = modeParam,
                alpn = params["alpn"] ?: "",
                allowInsecure = isTrueParam(params["allowInsecure"]) || isTrueParam(params["insecure"]),
                rawUri = rawUri,
                subscriptionId = subscriptionId,
                isAutoSelect = isAuto,
                sortOrder = sortOrder
            )
        }.getOrNull()
    }

    private fun parseVmess(rawUri: String, subscriptionId: String?, sortOrder: Int): ServerConfig? {
        return runCatching {
            val encoded = rawUri.substringAfter("://").trim()
            val jsonText = tryDecodeBase64(encoded) ?: return null
            val obj = JSONObject(jsonText)
            val host = obj.optString("add").takeIf { it.isNotBlank() } ?: return null
            val port = obj.optString("port").toIntOrNull() ?: obj.optInt("port", 443)
            val uuid = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
            val remark = obj.optString("ps").takeIf { it.isNotBlank() } ?: "$host:$port"
            val net = obj.optString("net", "tcp").lowercase()
            val transport = when (net) {
                "xhttp", "splithttp" -> TransportType.XHTTP
                "ws", "websocket" -> TransportType.WS
                "grpc" -> TransportType.GRPC
                "httpupgrade" -> TransportType.HTTPUPGRADE
                else -> TransportType.TCP
            }
            val tls = obj.optString("tls", "").lowercase()
            val security = when (tls) {
                "tls" -> SecurityType.TLS
                "reality" -> SecurityType.REALITY
                else -> SecurityType.NONE
            }
            val isAuto = detectAutoSelectMarker(remark, host, port)
            val (parsedName, parsedNote) = extractServerNameAndNote(remark)
            val vmessPath = if (transport == TransportType.GRPC) {
                obj.optString("path").ifBlank { obj.optString("serviceName", "") }
            } else {
                obj.optString("path", "/")
            }
            val vmessMode = obj.optString("type").ifBlank {
                if (transport == TransportType.GRPC) "gun" else "auto"
            }
            ServerConfig(
                id = UUID.randomUUID().toString(),
                name = parsedName,
                note = parsedNote,
                countryFlag = if (isAuto) "⚡" else detectCountryFlag(remark, host),
                protocol = VpnProtocol.VMESS,
                transport = transport,
                security = security,
                host = host,
                port = port,
                uuidOrPassword = uuid,
                encryption = obj.optString("scy", "auto"),
                sni = obj.optString("sni").ifBlank { obj.optString("host", "") },
                fingerprint = obj.optString("fp", "chrome"),
                path = vmessPath,
                hostHeader = obj.optString("authority").ifBlank { obj.optString("host", "") },
                xhttpMode = vmessMode,
                alpn = obj.optString("alpn", ""),
                allowInsecure = obj.optBoolean("allowInsecure", false) ||
                    isTrueParam(obj.optString("allowInsecure")) ||
                    isTrueParam(obj.optString("skip-cert-verify")),
                rawUri = rawUri,
                subscriptionId = subscriptionId,
                isAutoSelect = isAuto,
                sortOrder = sortOrder
            )
        }.getOrNull()
    }

    private fun parseShadowsocks(rawUri: String, subscriptionId: String?, sortOrder: Int): ServerConfig? {
        return runCatching {
            val withoutScheme = rawUri.substringAfter("://")
            val fragmentParts = withoutScheme.split("#", limit = 2)
            val remark = fragmentParts.getOrNull(1)?.let { decodeUrl(it) }?.takeIf { it.isNotBlank() }
            val beforeQuery = fragmentParts[0].split("?", limit = 2)[0].trimEnd('/')
            val atIdx = beforeQuery.lastIndexOf('@')
            val (userInfo, hostPort) = if (atIdx > 0) {
                val rawUser = decodeUrl(beforeQuery.substring(0, atIdx))
                val decodedUser = if (rawUser.contains(':')) rawUser else (tryDecodeBase64(rawUser) ?: rawUser)
                decodedUser to beforeQuery.substring(atIdx + 1).trimEnd('/')
            } else {
                val decodedAll = tryDecodeBase64(beforeQuery) ?: return null
                val innerAt = decodedAll.lastIndexOf('@')
                if (innerAt <= 0) return null
                decodedAll.substring(0, innerAt) to decodedAll.substring(innerAt + 1).trimEnd('/')
            }
            val methodPass = userInfo.split(":", limit = 2)
            val method = methodPass.getOrElse(0) { "aes-256-gcm" }
            val password = methodPass.getOrElse(1) { "" }
            val (host, port) = if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                val h = hostPort.substring(1, close)
                val p = hostPort.substring(close + 1).removePrefix(":").toIntOrNull() ?: return null
                h to p
            } else {
                val lastColon = hostPort.lastIndexOf(':')
                if (lastColon <= 0) return null
                val h = hostPort.substring(0, lastColon)
                val p = hostPort.substring(lastColon + 1).toIntOrNull() ?: return null
                h to p
            }
            val displayName = remark ?: "$host:$port"
            val isAuto = detectAutoSelectMarker(displayName, host, port)
            val (parsedName, parsedNote) = extractServerNameAndNote(displayName)
            ServerConfig(
                id = UUID.randomUUID().toString(),
                name = parsedName,
                note = parsedNote,
                countryFlag = if (isAuto) "⚡" else detectCountryFlag(displayName, host),
                protocol = VpnProtocol.SHADOWSOCKS,
                transport = TransportType.TCP,
                security = SecurityType.NONE,
                host = host,
                port = port,
                uuidOrPassword = password,
                encryption = method,
                rawUri = rawUri,
                subscriptionId = subscriptionId,
                isAutoSelect = isAuto,
                sortOrder = sortOrder
            )
        }.getOrNull()
    }

    private fun parseHysteria2(rawUri: String, subscriptionId: String?, sortOrder: Int): ServerConfig? {
        return runCatching {
            val schemeIdx = rawUri.indexOf("://")
            if (schemeIdx < 0) return null
            val afterScheme = rawUri.substring(schemeIdx + 3)

            val hashIdx = afterScheme.indexOf('#')
            val beforeFragment = if (hashIdx >= 0) afterScheme.substring(0, hashIdx) else afterScheme
            val fragment = if (hashIdx >= 0) afterScheme.substring(hashIdx + 1) else null

            val queryIdx = beforeFragment.indexOf('?')
            val authorityAndPath = if (queryIdx >= 0) beforeFragment.substring(0, queryIdx) else beforeFragment
            val query = if (queryIdx >= 0) beforeFragment.substring(queryIdx + 1) else null

            val authority = authorityAndPath.substringBefore('/')
            val atIdx = authority.lastIndexOf('@')
            if (atIdx <= 0) return null
            val auth = decodeUrl(authority.substring(0, atIdx))
            val hostPortRaw = authority.substring(atIdx + 1).trim()
            if (hostPortRaw.isEmpty()) return null

            var mportFromHost = ""
            val (host, port) = if (hostPortRaw.startsWith("[")) {
                val close = hostPortRaw.indexOf(']')
                if (close < 0) return null
                val h = hostPortRaw.substring(1, close)
                val tail = hostPortRaw.substring(close + 1).removePrefix(":")
                val firstPortPart = tail.substringBefore(',').trim()
                if (tail.contains(',')) mportFromHost = tail.substringAfter(',').trim()
                h to (firstPortPart.toIntOrNull() ?: 443)
            } else {
                val colonIdx = hostPortRaw.lastIndexOf(':')
                if (colonIdx > 0) {
                    val h = hostPortRaw.substring(0, colonIdx)
                    val tail = hostPortRaw.substring(colonIdx + 1).trim()
                    val firstPortPart = tail.substringBefore(',').substringBefore('-').trim()
                    if (tail.contains(',') || tail.contains('-')) mportFromHost = tail
                    h to (firstPortPart.toIntOrNull() ?: 443)
                } else {
                    hostPortRaw to 443
                }
            }
            if (host.isBlank()) return null

            val params = parseQueryParams(query)
            val remark = fragment?.let { decodeUrl(it) }?.takeIf { it.isNotBlank() } ?: "$host:$port"
            val isAuto = detectAutoSelectMarker(remark, host, port)
            val (parsedName, parsedNote) = extractServerNameAndNote(remark)

            val obfs = params["obfs"]?.trim().orEmpty()
            val obfsPassword = params["obfs-password"] ?: params["obfs_password"] ?: ""
            val pinSha = (params["pinSHA256"] ?: params["pinsha256"] ?: "").replace(":", "").lowercase()
            val mport = params["mport"] ?: mportFromHost
            val upMbps = parseBandwidthMbps(params["upmbps"] ?: params["up"])
            val downMbps = parseBandwidthMbps(params["downmbps"] ?: params["down"])

            ServerConfig(
                id = UUID.randomUUID().toString(),
                name = parsedName,
                note = parsedNote,
                countryFlag = if (isAuto) "⚡" else detectCountryFlag(remark, host),
                protocol = VpnProtocol.HYSTERIA2,
                transport = TransportType.TCP,
                security = SecurityType.TLS,
                host = host,
                port = port,
                uuidOrPassword = auth,
                sni = params["sni"] ?: params["peer"] ?: host,
                alpn = params["alpn"] ?: "h3",
                allowInsecure = isTrueParam(params["insecure"]) || isTrueParam(params["allowInsecure"]),
                rawUri = rawUri,
                subscriptionId = subscriptionId,
                isAutoSelect = isAuto,
                sortOrder = sortOrder,
                obfs = obfs,
                obfsPassword = obfsPassword,
                pinSha256 = pinSha,
                upMbps = upMbps,
                downMbps = downMbps,
                ports = mport
            )
        }.getOrNull()
    }

    private fun parseBandwidthMbps(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return Regex("""\d+""").find(raw)?.value?.toIntOrNull() ?: 0
    }

    private fun isTrueParam(value: String?): Boolean {
        val v = value?.trim()?.lowercase() ?: return false
        return v == "1" || v == "true" || v == "yes"
    }

    private fun parseJsonConfigs(rawJson: String, subscriptionId: String?): List<ServerConfig> {
        val result = mutableListOf<ServerConfig>()
        runCatching {
            val trimmed = rawJson.trim()
            val blocks = extractTopLevelJsonBlocks(trimmed).ifEmpty { listOf(trimmed) }
            val entries = mutableListOf<Any>() // JSONObject or URI String
            blocks.forEach { block ->
                val b = block.trim()
                if (b.startsWith("[")) {
                    val arr = JSONArray(b)
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
                        when (item) {
                            is JSONObject -> entries.add(item)
                            is String -> if (item.isNotBlank()) entries.add(item)
                        }
                    }
                } else if (b.startsWith("{")) {
                    entries.add(JSONObject(b))
                }
            }

            val supportedProtocols = setOf(
                "vless", "vmess", "trojan", "shadowsocks", "ss", "hysteria", "hysteria2", "hy2"
            )

            entries.forEach { entry ->
                if (entry is String) {
                    parseSingleUri(entry, subscriptionId, result.size)?.let { result.add(it) }
                    return@forEach
                }
                val obj = entry as JSONObject
                val remarks = obj.optString("remarks", obj.optString("tag", "")).trim()
                val metaDescription = obj.optJSONObject("meta")?.optString("serverDescription")?.trim().orEmpty()
                val outbounds = obj.optJSONArray("outbounds")
                if (outbounds == null) {
                    // Direct single outbound JSON object
                    val directType = obj.optString("protocol").ifBlank { obj.optString("type") }.lowercase()
                    if (directType in supportedProtocols) {
                        parseJsonOutbound(obj, remarks, subscriptionId, result.size)?.let { base ->
                            val updatedNote = base.note.ifBlank { metaDescription }
                            result.add(base.copy(note = updatedNote))
                        }
                    }
                    return@forEach
                }

                val proxyCandidates = mutableListOf<JSONObject>()
                var autoSelectNode: ServerConfig? = null

                for (i in 0 until outbounds.length()) {
                    val ob = outbounds.optJSONObject(i) ?: continue
                    val protocol = ob.optString("protocol").lowercase()
                    val type = ob.optString("type").lowercase()
                    val obTag = ob.optString("tag", "").lowercase()

                    if (obTag == "direct" || obTag == "block" || obTag == "dns-out" ||
                        protocol == "freedom" || protocol == "blackhole" || protocol == "dns" ||
                        type == "direct" || type == "block" || type == "dns"
                    ) {
                        continue
                    }

                    if (protocol in supportedProtocols || type in supportedProtocols) {
                        proxyCandidates.add(ob)
                    } else if ((type == "urltest" || type == "selector") && autoSelectNode == null) {
                        val tag = ob.optString("tag", "Выбор узла")
                        if (tag.contains("auto", true) || tag.contains("авто", true) || tag.contains("wifi", true) || tag.contains("lte", true)) {
                            autoSelectNode = createAutoSelectNode(tag, subscriptionId, result.size)
                        }
                    }
                }

                val primaryOb = proxyCandidates.firstOrNull {
                    it.optString("tag").equals("proxy", ignoreCase = true)
                } ?: proxyCandidates.firstOrNull()

                val routingObj = obj.optJSONObject("routing")
                val hasBalancers = routingObj?.optJSONArray("balancers")?.let { it.length() > 0 } == true
                val hasRoutingRules = routingObj?.optJSONArray("rules")?.let { it.length() > 0 } == true
                val hasObservatory = obj.has("observatory") || obj.has("burstObservatory")
                val primaryStream = primaryOb?.optJSONObject("streamSettings")
                val hasAdvancedStreamFields = primaryStream != null && (
                    primaryStream.has("finalmask") ||
                        primaryStream.optJSONObject("realitySettings")?.has("mldsa65Verify") == true ||
                        primaryStream.optJSONObject("realitySettings")?.has("mlkem768") == true
                    )
                val isFullXrayJson = proxyCandidates.size > 1 ||
                    hasBalancers ||
                    hasObservatory ||
                    hasRoutingRules ||
                    obj.has("routing") ||
                    obj.has("inbounds") ||
                    obj.has("dns") ||
                    hasAdvancedStreamFields

                val prettyJson = obj.toString(2)
                val compactJson = obj.toString()

                if (primaryOb != null) {
                    parseJsonOutbound(primaryOb, remarks, subscriptionId, result.size)?.let { base ->
                        val finalServer = if (isFullXrayJson) {
                            val (parsedName, parsedNote) = if (remarks.isNotBlank()) {
                                extractServerNameAndNote(remarks)
                            } else {
                                base.name to base.note
                            }
                            val resolvedFlag = if (remarks.isNotBlank()) {
                                detectCountryFlag(remarks, base.host)
                            } else {
                                base.countryFlag
                            }
                            val combinedNote = parsedNote
                                .ifBlank { base.note }
                                .ifBlank { metaDescription }
                            base.copy(
                                name = parsedName.ifBlank { base.name },
                                note = combinedNote,
                                countryFlag = resolvedFlag,
                                isAutoSelect = false,
                                rawUri = compactJson,
                                customJsonConfig = prettyJson
                            )
                        } else {
                            if (metaDescription.isNotBlank() && base.note.isBlank()) {
                                base.copy(note = metaDescription)
                            } else {
                                base
                            }
                        }
                        result.add(finalServer)
                    }
                } else if (autoSelectNode != null) {
                    result.add(
                        if (isFullXrayJson) {
                            val (parsedName, parsedNote) = if (remarks.isNotBlank()) {
                                extractServerNameAndNote(remarks)
                            } else {
                                autoSelectNode.name to ""
                            }
                            autoSelectNode.copy(
                                name = parsedName.ifBlank { autoSelectNode.name },
                                note = parsedNote.ifBlank { metaDescription },
                                countryFlag = if (remarks.isNotBlank()) detectCountryFlag(remarks, autoSelectNode.host) else autoSelectNode.countryFlag,
                                isAutoSelect = false,
                                rawUri = compactJson,
                                customJsonConfig = prettyJson
                            )
                        } else {
                            autoSelectNode
                        }
                    )
                }
            }
        }
        return result
    }

    private fun parseJsonOutbound(
        ob: JSONObject,
        fallbackName: String,
        subscriptionId: String?,
        sortOrder: Int
    ): ServerConfig? {
        return runCatching {
            val protoRaw = ob.optString("protocol").ifBlank { ob.optString("type") }.lowercase()
            val protocol = when (protoRaw) {
                "vless" -> VpnProtocol.VLESS
                "vmess" -> VpnProtocol.VMESS
                "trojan" -> VpnProtocol.TROJAN
                "shadowsocks", "ss" -> VpnProtocol.SHADOWSOCKS
                "hysteria", "hysteria2", "hy2" -> VpnProtocol.HYSTERIA2
                else -> return null
            }

            val settings = ob.optJSONObject("settings")
            val vnext = settings?.optJSONArray("vnext")?.optJSONObject(0)
            val srvObj = settings?.optJSONArray("servers")?.optJSONObject(0)
            val stream = ob.optJSONObject("streamSettings")
            val sbTransport = ob.optJSONObject("transport")
            val sbTls = ob.optJSONObject("tls")

            val host = vnext?.optString("address")?.takeIf { it.isNotBlank() }
                ?: srvObj?.optString("address")?.takeIf { it.isNotBlank() }
                ?: settings?.optString("address")?.takeIf { it.isNotBlank() }
                ?: ob.optString("server").takeIf { it.isNotBlank() }
                ?: return null
            val port = vnext?.optInt("port", 0)?.takeIf { it > 0 }
                ?: srvObj?.optInt("port", 0)?.takeIf { it > 0 }
                ?: settings?.optInt("port", 0)?.takeIf { it > 0 }
                ?: ob.optInt("server_port", 443)

            val user = vnext?.optJSONArray("users")?.optJSONObject(0)
            val hysteriaSettings = stream?.optJSONObject("hysteriaSettings")

            val uuidOrPass = when (protocol) {
                VpnProtocol.VLESS, VpnProtocol.VMESS -> {
                    user?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: ob.optString("uuid").takeIf { it.isNotBlank() }
                        ?: return null
                }
                VpnProtocol.TROJAN, VpnProtocol.SHADOWSOCKS -> {
                    srvObj?.optString("password")?.takeIf { it.isNotBlank() }
                        ?: ob.optString("password").takeIf { it.isNotBlank() }
                        ?: return null
                }
                VpnProtocol.HYSTERIA2 -> {
                    hysteriaSettings?.optString("auth")?.takeIf { it.isNotBlank() }
                        ?: ob.optString("password").takeIf { it.isNotBlank() }
                        ?: settings?.optString("auth")?.takeIf { it.isNotBlank() }
                        ?: ""
                }
            }

            val flow = user?.optString("flow", "") ?: ob.optString("flow", "")
            val encryption = when (protocol) {
                VpnProtocol.SHADOWSOCKS -> srvObj?.optString("method")?.ifBlank { null }
                    ?: ob.optString("method", "aes-256-gcm")
                VpnProtocol.VMESS -> user?.optString("security")?.ifBlank { null }
                    ?: ob.optString("security", "auto")
                else -> user?.optString("encryption", "none") ?: "none"
            }

            val net = (stream?.optString("network") ?: sbTransport?.optString("type") ?: "tcp").lowercase()
            val transport = when (net) {
                "xhttp", "splithttp" -> TransportType.XHTTP
                "ws", "websocket" -> TransportType.WS
                "grpc" -> TransportType.GRPC
                "httpupgrade" -> TransportType.HTTPUPGRADE
                else -> TransportType.TCP
            }

            val sec = stream?.optString("security")?.lowercase()
                ?: if (sbTls?.optBoolean("enabled", false) == true) {
                    if (sbTls.optJSONObject("reality")?.optBoolean("enabled", false) == true) "reality" else "tls"
                } else if (protocol == VpnProtocol.HYSTERIA2) "tls" else "none"

            val security = when (sec) {
                "reality" -> SecurityType.REALITY
                "tls" -> SecurityType.TLS
                else -> SecurityType.NONE
            }

            val reality = stream?.optJSONObject("realitySettings") ?: sbTls?.optJSONObject("reality")
            val tls = stream?.optJSONObject("tlsSettings") ?: sbTls
            val xhttp = stream?.optJSONObject("xhttpSettings") ?: stream?.optJSONObject("splithttpSettings")
            val ws = stream?.optJSONObject("wsSettings")
            val grpc = stream?.optJSONObject("grpcSettings")

            val sni = reality?.optString("serverName")?.takeIf { it.isNotBlank() }
                ?: tls?.optString("serverName")?.takeIf { it.isNotBlank() }
                ?: tls?.optString("server_name")?.takeIf { it.isNotBlank() }
                ?: if (protocol == VpnProtocol.HYSTERIA2) host else ""
            val fp = reality?.optString("fingerprint")?.takeIf { it.isNotBlank() }
                ?: tls?.optString("fingerprint")?.takeIf { it.isNotBlank() }
                ?: sbTls?.optJSONObject("utls")?.optString("fingerprint")?.takeIf { it.isNotBlank() }
                ?: "chrome"
            val pbk = reality?.optString("publicKey")?.ifBlank { reality.optString("public_key", "") } ?: ""
            val sid = reality?.optString("shortId")?.ifBlank { reality.optString("short_id", "") } ?: ""
            val spx = reality?.optString("spiderX") ?: ""

            val path = when {
                transport == TransportType.GRPC -> grpc?.optString("serviceName")
                    ?.ifBlank { sbTransport?.optString("service_name", "") }
                    ?: ""
                xhttp != null -> xhttp.optString("path", "/")
                ws != null -> ws.optString("path", "/")
                sbTransport != null -> sbTransport.optString("path", "/")
                else -> "/"
            }
            val hostHeader = when {
                transport == TransportType.GRPC -> grpc?.optString("authority", "") ?: ""
                xhttp != null -> xhttp.optString("host", "")
                ws != null -> ws.optJSONObject("headers")?.optString("Host", "") ?: ""
                else -> sbTransport?.optJSONObject("headers")?.optString("Host", "") ?: ""
            }
            val mode = when {
                transport == TransportType.GRPC -> if (grpc?.optBoolean("multiMode", false) == true) "multi" else "gun"
                xhttp != null -> xhttp.optString("mode", "auto")
                else -> "auto"
            }
            val allowInsecure = tls?.optBoolean("allowInsecure", false) == true ||
                tls?.optBoolean("insecure", false) == true

            val alpnArr = tls?.optJSONArray("alpn")
            val alpn = if (alpnArr != null && alpnArr.length() > 0) {
                (0 until alpnArr.length()).mapNotNull { alpnArr.optString(it).takeIf { s -> s.isNotBlank() } }
                    .joinToString(",")
            } else if (protocol == VpnProtocol.HYSTERIA2) "h3" else ""

            val sbObfs = ob.optJSONObject("obfs")
            val udpMask = stream?.optJSONObject("finalmask")?.optJSONArray("udp")?.optJSONObject(0)
                ?: stream?.optJSONArray("udpmasks")?.optJSONObject(0)
            val obfsType = udpMask?.optString("type")?.ifBlank { null }
                ?: sbObfs?.optString("type").orEmpty()
            val obfsPass = udpMask?.optJSONObject("settings")?.optString("password")?.ifBlank { null }
                ?: sbObfs?.optString("password").orEmpty()

            val name = fallbackName.ifBlank { ob.optString("tag").ifBlank { "$host:$port" } }
            val explicitNote = ob.optString("note").ifBlank {
                ob.optString("badge").ifBlank { ob.optString("comment", "") }
            }.trim()
            val (parsedName, remarkNote) = extractServerNameAndNote(name)
            val combinedNote = explicitNote.ifBlank { remarkNote }
            val isAuto = detectAutoSelectMarker(name, host, port)

            val synthesizedUri = when (protocol) {
                VpnProtocol.VLESS -> "vless://$uuidOrPass@$host:$port?type=${transport.raw}&security=${security.raw}&sni=$sni&fp=$fp&pbk=$pbk&sid=$sid&flow=$flow#${name}"
                VpnProtocol.TROJAN -> "trojan://$uuidOrPass@$host:$port?type=${transport.raw}&security=${security.raw}&sni=$sni&fp=$fp#${name}"
                VpnProtocol.HYSTERIA2 -> buildString {
                    append("hysteria2://$uuidOrPass@$host:$port?sni=$sni")
                    if (obfsType.isNotBlank()) append("&obfs=$obfsType&obfs-password=$obfsPass")
                    if (allowInsecure) append("&insecure=1")
                    append("#$name")
                }
                VpnProtocol.SHADOWSOCKS -> "ss://${Base64.encodeToString("$encryption:$uuidOrPass".toByteArray(), Base64.NO_WRAP)}@$host:$port#$name"
                VpnProtocol.VMESS -> "vmess://" + Base64.encodeToString(
                    JSONObject().apply {
                        put("v", "2")
                        put("ps", name)
                        put("add", host)
                        put("port", port)
                        put("id", uuidOrPass)
                        put("net", transport.raw)
                        put("tls", if (security == SecurityType.TLS) "tls" else "")
                        put("sni", sni)
                        put("host", hostHeader)
                        put("path", path)
                    }.toString().toByteArray(),
                    Base64.NO_WRAP
                )
            }

            ServerConfig(
                id = UUID.randomUUID().toString(),
                name = parsedName,
                note = combinedNote,
                countryFlag = if (isAuto) "⚡" else detectCountryFlag(name, host),
                protocol = protocol,
                transport = transport,
                security = security,
                host = host,
                port = port,
                uuidOrPassword = uuidOrPass,
                flow = flow,
                encryption = encryption,
                sni = sni,
                fingerprint = fp,
                publicKey = pbk,
                shortId = sid,
                spiderX = spx,
                path = path,
                hostHeader = hostHeader,
                xhttpMode = mode,
                alpn = alpn,
                allowInsecure = allowInsecure,
                rawUri = synthesizedUri,
                subscriptionId = subscriptionId,
                isAutoSelect = isAuto,
                sortOrder = sortOrder,
                obfs = obfsType,
                obfsPassword = obfsPass,
                upMbps = parseBandwidthMbps(hysteriaSettings?.optString("up")).takeIf { it > 0 }
                    ?: ob.optInt("up_mbps", 0),
                downMbps = parseBandwidthMbps(hysteriaSettings?.optString("down")).takeIf { it > 0 }
                    ?: ob.optInt("down_mbps", 0)
            )
        }.getOrNull()
    }

    private fun parseClashYaml(yaml: String, subscriptionId: String?): List<ServerConfig> {
        val result = mutableListOf<ServerConfig>()
        val lines = yaml.lines()
        var current = mutableMapOf<String, String>()
        var inProxies = false
        var currentSection: String? = null
        var sectionIndent = Int.MAX_VALUE

        fun flushProxy() {
            if (current.isEmpty()) return
            val type = current["type"]?.lowercase() ?: return
            val name = current["name"] ?: "Proxy"
            val server = current["server"] ?: return
            val port = current["port"]?.toIntOrNull() ?: 443
            val (parsedName, parsedNote) = extractServerNameAndNote(name)
            val isAuto = detectAutoSelectMarker(name, server, port)

            val network = when (current["network"]?.lowercase()) {
                "xhttp", "splithttp" -> TransportType.XHTTP
                "ws" -> TransportType.WS
                "grpc" -> TransportType.GRPC
                "httpupgrade" -> TransportType.HTTPUPGRADE
                else -> TransportType.TCP
            }
            val sni = current["servername"] ?: current["sni"] ?: ""
            val fp = current["client-fingerprint"] ?: "chrome"
            val allowInsecure = isTrueParam(current["skip-cert-verify"])
            val pathOrService = when (network) {
                TransportType.GRPC -> current["grpc-service-name"] ?: current["serviceName"] ?: ""
                TransportType.WS -> current["ws-path"] ?: current["path"] ?: "/"
                else -> current["path"] ?: "/"
            }
            val hostHeader = current["ws-host"] ?: current["Host"] ?: current["host"] ?: ""
            val alpn = current["alpn"] ?: ""

            when (type) {
                "vless" -> {
                    val uuid = current["uuid"] ?: return
                    val pbk = current["public-key"] ?: ""
                    val sid = current["short-id"] ?: ""
                    val tlsEnabled = isTrueParam(current["tls"]) || pbk.isNotEmpty()
                    val security = when {
                        pbk.isNotEmpty() -> SecurityType.REALITY
                        tlsEnabled -> SecurityType.TLS
                        else -> SecurityType.NONE
                    }
                    val flow = current["flow"] ?: ""
                    val uri = "vless://$uuid@$server:$port?type=${network.raw}&security=${security.raw}&sni=$sni&fp=$fp&pbk=$pbk&sid=$sid&flow=$flow&serviceName=$pathOrService#$name"
                    result.add(
                        ServerConfig(
                            id = UUID.randomUUID().toString(),
                            name = parsedName,
                            note = parsedNote,
                            countryFlag = if (isAuto) "⚡" else detectCountryFlag(name, server),
                            protocol = VpnProtocol.VLESS,
                            transport = network,
                            security = security,
                            host = server,
                            port = port,
                            uuidOrPassword = uuid,
                            flow = flow,
                            sni = sni,
                            fingerprint = fp,
                            publicKey = pbk,
                            shortId = sid,
                            path = pathOrService,
                            hostHeader = hostHeader,
                            alpn = alpn,
                            allowInsecure = allowInsecure,
                            rawUri = uri,
                            subscriptionId = subscriptionId,
                            isAutoSelect = isAuto,
                            sortOrder = result.size
                        )
                    )
                }
                "vmess" -> {
                    val uuid = current["uuid"] ?: return
                    val tlsEnabled = isTrueParam(current["tls"])
                    val security = if (tlsEnabled) SecurityType.TLS else SecurityType.NONE
                    val cipher = current["cipher"] ?: "auto"
                    val uri = "vmess://" + Base64.encodeToString(
                        JSONObject().apply {
                            put("v", "2")
                            put("ps", name)
                            put("add", server)
                            put("port", port)
                            put("id", uuid)
                            put("net", network.raw)
                            put("tls", if (tlsEnabled) "tls" else "")
                            put("sni", sni)
                            put("host", hostHeader)
                            put("path", pathOrService)
                        }.toString().toByteArray(),
                        Base64.NO_WRAP
                    )
                    result.add(
                        ServerConfig(
                            id = UUID.randomUUID().toString(),
                            name = parsedName,
                            note = parsedNote,
                            countryFlag = if (isAuto) "⚡" else detectCountryFlag(name, server),
                            protocol = VpnProtocol.VMESS,
                            transport = network,
                            security = security,
                            host = server,
                            port = port,
                            uuidOrPassword = uuid,
                            encryption = cipher,
                            sni = sni,
                            fingerprint = fp,
                            path = pathOrService,
                            hostHeader = hostHeader,
                            alpn = alpn,
                            allowInsecure = allowInsecure,
                            rawUri = uri,
                            subscriptionId = subscriptionId,
                            isAutoSelect = isAuto,
                            sortOrder = result.size
                        )
                    )
                }
                "trojan" -> {
                    val password = current["password"] ?: return
                    val pbk = current["public-key"] ?: ""
                    val sid = current["short-id"] ?: ""
                    val security = if (pbk.isNotEmpty()) SecurityType.REALITY else SecurityType.TLS
                    val resolvedSni = sni.ifBlank { server }
                    val uri = "trojan://$password@$server:$port?type=${network.raw}&security=${security.raw}&sni=$resolvedSni&fp=$fp&serviceName=$pathOrService#$name"
                    result.add(
                        ServerConfig(
                            id = UUID.randomUUID().toString(),
                            name = parsedName,
                            note = parsedNote,
                            countryFlag = if (isAuto) "⚡" else detectCountryFlag(name, server),
                            protocol = VpnProtocol.TROJAN,
                            transport = network,
                            security = security,
                            host = server,
                            port = port,
                            uuidOrPassword = password,
                            sni = resolvedSni,
                            fingerprint = fp,
                            publicKey = pbk,
                            shortId = sid,
                            path = pathOrService,
                            hostHeader = hostHeader,
                            alpn = alpn,
                            allowInsecure = allowInsecure,
                            rawUri = uri,
                            subscriptionId = subscriptionId,
                            isAutoSelect = isAuto,
                            sortOrder = result.size
                        )
                    )
                }
                "ss" -> {
                    val password = current["password"] ?: return
                    val cipher = current["cipher"] ?: "aes-256-gcm"
                    val uri = "ss://${Base64.encodeToString("$cipher:$password".toByteArray(), Base64.NO_WRAP)}@$server:$port#$name"
                    result.add(
                        ServerConfig(
                            id = UUID.randomUUID().toString(),
                            name = parsedName,
                            note = parsedNote,
                            countryFlag = if (isAuto) "⚡" else detectCountryFlag(name, server),
                            protocol = VpnProtocol.SHADOWSOCKS,
                            transport = TransportType.TCP,
                            security = SecurityType.NONE,
                            host = server,
                            port = port,
                            uuidOrPassword = password,
                            encryption = cipher,
                            rawUri = uri,
                            subscriptionId = subscriptionId,
                            isAutoSelect = isAuto,
                            sortOrder = result.size
                        )
                    )
                }
                "hysteria2", "hy2" -> {
                    val password = current["password"] ?: current["auth"] ?: ""
                    val obfs = current["obfs"] ?: ""
                    val obfsPassword = current["obfs-password"] ?: ""
                    val resolvedSni = sni.ifBlank { server }
                    val uri = buildString {
                        append("hysteria2://$password@$server:$port?sni=$resolvedSni")
                        if (obfs.isNotBlank()) append("&obfs=$obfs&obfs-password=$obfsPassword")
                        if (allowInsecure) append("&insecure=1")
                        append("#$name")
                    }
                    result.add(
                        ServerConfig(
                            id = UUID.randomUUID().toString(),
                            name = parsedName,
                            note = parsedNote,
                            countryFlag = if (isAuto) "⚡" else detectCountryFlag(name, server),
                            protocol = VpnProtocol.HYSTERIA2,
                            transport = TransportType.TCP,
                            security = SecurityType.TLS,
                            host = server,
                            port = port,
                            uuidOrPassword = password,
                            sni = resolvedSni,
                            alpn = alpn.ifBlank { "h3" },
                            allowInsecure = allowInsecure,
                            rawUri = uri,
                            subscriptionId = subscriptionId,
                            isAutoSelect = isAuto,
                            sortOrder = result.size,
                            obfs = obfs,
                            obfsPassword = obfsPassword,
                            upMbps = parseBandwidthMbps(current["up"]),
                            downMbps = parseBandwidthMbps(current["down"]),
                            ports = current["ports"] ?: ""
                        )
                    )
                }
            }
        }

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val indent = rawLine.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            if (trimmed.startsWith("proxies:")) {
                inProxies = true
                continue
            }
            if (inProxies && indent == 0 && trimmed.endsWith(":") && !trimmed.startsWith("-")) {
                flushProxy()
                break
            }
            if (!inProxies) continue
            if (trimmed.startsWith("- ")) {
                flushProxy()
                current = mutableMapOf()
                currentSection = null
                sectionIndent = Int.MAX_VALUE
                val rest = trimmed.removePrefix("- ").trim()
                parseYamlKeyVal(rest, current, prefix = null)
            } else if (trimmed.contains(":")) {
                if (indent <= sectionIndent) {
                    currentSection = null
                    sectionIndent = Int.MAX_VALUE
                }
                val key = trimmed.substringBefore(':').trim().trim('"', '\'')
                val value = trimmed.substringAfter(':').trim()
                if (value.isEmpty()) {
                    currentSection = key.lowercase()
                    sectionIndent = indent
                } else {
                    val mappedPrefix = when (currentSection) {
                        "grpc-opts" -> "grpc-"
                        "ws-opts" -> "ws-"
                        else -> null
                    }
                    parseYamlKeyVal(trimmed, current, prefix = mappedPrefix)
                }
            }
        }
        flushProxy()
        return result
    }

    private fun parseYamlKeyVal(line: String, map: MutableMap<String, String>, prefix: String?) {
        val clean = line.trim().trimStart('{').trimEnd('}')
        clean.split(",").forEach { part ->
            val idx = part.indexOf(':')
            if (idx > 0) {
                val k = part.substring(0, idx).trim().trim('"', '\'')
                val v = part.substring(idx + 1).trim().trim('"', '\'')
                if (k.isNotEmpty()) {
                    map[k] = v
                    if (prefix != null && (k == "service-name" || k == "path" || k == "host" || k == "Host")) {
                        map["$prefix${k.lowercase()}"] = v
                        if (k.equals("host", ignoreCase = true)) {
                            map["ws-host"] = v
                        }
                    }
                }
            }
        }
    }

    private fun createAutoSelectNode(name: String, subscriptionId: String?, sortOrder: Int): ServerConfig {
        return ServerConfig(
            id = UUID.randomUUID().toString(),
            name = cleanServerName(name),
            countryFlag = "⚡",
            protocol = VpnProtocol.VLESS,
            transport = TransportType.TCP,
            security = SecurityType.REALITY,
            host = "auto.finivpn.local",
            port = 443,
            uuidOrPassword = "auto",
            rawUri = "vless://auto@auto.finivpn.local:443#$name",
            subscriptionId = subscriptionId,
            isAutoSelect = true,
            sortOrder = sortOrder
        )
    }

    private fun detectAutoSelectMarker(remark: String, host: String, port: Int): Boolean {
        val r = remark.lowercase()
        val isAutoRemark = r.contains("автовыбор") || r.contains("auto-select") || r.contains("autoselect")
        val isDummyEndpoint = host == "0.0.0.0" || host == "127.0.0.1" || port <= 1
        return isAutoRemark && isDummyEndpoint
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else decodeUrl(pair.substring(0, idx)) to decodeUrl(pair.substring(idx + 1))
        }.toMap()
    }

    private fun decodeUrl(value: String): String =
        runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

    fun tryDecodeBase64(input: String): String? {
        val cleaned = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.length < 4) return null
        val normalized = cleaned.replace('-', '+').replace('_', '/')
        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> normalized
        }
        return runCatching {
            val bytes = Base64.decode(padded, Base64.DEFAULT)
            val str = String(bytes, Charsets.UTF_8)
            if (str.any { it.code < 9 || (it.code in 14..31) }) null else str
        }.getOrNull()
    }

    private fun cleanServerName(remark: String): String {
        val flag = extractLeadingFlag(remark)
        return if (flag != null) {
            remark.removePrefix(flag).trim().ifEmpty { remark.trim() }
        } else {
            remark.trim()
        }
    }

    /**
     * Extracts optional carrier / plan notes (e.g. "Безлимит", "Кроме МТС", "Кроме Т2", "Только Wi-Fi")
     * from server remark while leaving ordinary speed/server labels in the main title.
     * Returns Pair(cleanName, extractedNote).
     */
    fun extractServerNameAndNote(remark: String): Pair<String, String> {
        var working = cleanServerName(remark)
        if (working.isBlank()) return "" to ""

        val notes = mutableListOf<String>()

        // 1. Bracketed / parenthesized note tags: (Безлимит), [Кроме МТС], (Кроме Tele2 / T2), (Только Wi-Fi)
        val bracketPattern = Regex(
            """[\(\[]\s*((?:безлимит\w*|unlimit\w*|кроме\s+[^\]\)]+|only\s+[^\]\)]+|только\s+[^\]\)]+|для\s+[^\]\)]+|whitelist|белые?\s+списк\w*)[^\]\)]*)\s*[\)\]]""",
            RegexOption.IGNORE_CASE
        )
        working = bracketPattern.replace(working) { m ->
            val tag = m.groupValues[1].trim()
            if (tag.isNotBlank()) notes.add(formatNoteTag(tag))
            " "
        }

        // 2. Pipe / dash / bullet separated segments (e.g. "🇫🇮 Финляндия | Безлимит | Кроме МТС")
        val sepParts = working.split(Regex("""\s*[|•]\s*|\s+-\s+|\s+—\s+"""))
        if (sepParts.size > 1) {
            val kept = mutableListOf<String>()
            sepParts.forEach { part ->
                val trimmedPart = part.trim()
                if (trimmedPart.isNotEmpty() && isCarrierOrPlanNote(trimmedPart)) {
                    notes.add(formatNoteTag(trimmedPart))
                } else if (trimmedPart.isNotEmpty()) {
                    kept.add(trimmedPart)
                }
            }
            if (kept.isNotEmpty()) {
                working = kept.joinToString(" • ")
            }
        }

        // 3. Inline trailing keywords without brackets when preceded by text
        val inlinePatterns = listOf(
            Regex("""(?i)\b(кроме\s+(?:мтс|билайн\w*|мегафон\w*|т2|t2|tele2|йота|yota|тинькофф|ростелеком\w*|wi-?fi|lte|4g|5g|[a-zа-яё0-9]{2,15}))$"""),
            Regex("""(?i)\b(безлимит(?:ный|ка)?|unlimited)$"""),
            Regex("""(?i)\b(только\s+(?:wi-?fi|lte|4g|5g|мтс|т2|билайн|мегафон))$""")
        )
        for (rx in inlinePatterns) {
            val match = rx.find(working.trim())
            if (match != null && working.length > match.value.length + 2) {
                notes.add(formatNoteTag(match.groupValues[1]))
                working = working.removeRange(match.range).trim()
            }
        }

        val finalName = working
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', '|', '-', '—', '•', ',')
            .ifBlank { cleanServerName(remark) }

        val finalNote = notes
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(" • ")

        return finalName to finalNote
    }

    private fun isCarrierOrPlanNote(segment: String): Boolean {
        val s = segment.trim().lowercase()
        return s.startsWith("безлимит") ||
            s.startsWith("unlimit") ||
            s.startsWith("кроме ") ||
            s.startsWith("только ") ||
            s.startsWith("only ") ||
            s.contains("белые списки") ||
            s.contains("белых списков") ||
            s == "whitelist"
    }

    private fun formatNoteTag(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return ""
        return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun extractLeadingFlag(text: String): String? {
        val cps = text.codePoints().toArray()
        for (i in 0 until cps.size - 1) {
            if (cps[i] in 0x1F1E6..0x1F1FF && cps[i + 1] in 0x1F1E6..0x1F1FF) {
                return String(intArrayOf(cps[i], cps[i + 1]), 0, 2)
            }
        }
        return null
    }

    fun detectCountryFlag(remark: String, host: String): String {
        extractLeadingFlag(remark)?.let { return it }
        val combined = "$remark $host".lowercase()
        val map = listOf(
            listOf("germany", "deutschland", "frankfurt", "герман", "франкфурт", "🇩🇪", " de ", "[de]", "-de-", ".de") to "🇩🇪",
            listOf("netherlands", "holland", "amsterdam", "нидерланд", "голлан", "амстердам", " nl ", "[nl]", "-nl-", ".nl") to "🇳🇱",
            listOf("finland", "helsinki", "финлянд", "хельсинк", " fi ", "[fi]", "-fi-", ".fi") to "🇫🇮",
            listOf("united states", "usa", "america", "new york", "los angeles", "dallas", "miami", "сша", "америк", " us ", "[us]", "-us-") to "🇺🇸",
            listOf("united kingdom", "britain", "england", "london", "великобрит", "лондон", "англия", " gb ", " uk ", "[uk]", "[gb]") to "🇬🇧",
            listOf("france", "paris", "франц", "париж", " fr ", "[fr]", "-fr-", ".fr") to "🇫🇷",
            listOf("turkey", "istanbul", "турц", "стамбул", " tr ", "[tr]", "-tr-", ".tr") to "🇹🇷",
            listOf("poland", "warsaw", "польш", "варшав", " pl ", "[pl]", "-pl-", ".pl") to "🇵🇱",
            listOf("sweden", "stockholm", "швец", "стокгольм", " se ", "[se]", "-se-") to "🇸🇪",
            listOf("switzerland", "zurich", "швейцар", "цюрих", " ch ", "[ch]", "-ch-") to "🇨🇭",
            listOf("austria", "vienna", "австр", "вена", " at ", "[at]", "-at-") to "🇦🇹",
            listOf("latvia", "riga", "латв", "рига", " lv ", "[lv]", "-lv-") to "🇱🇻",
            listOf("estonia", "tallinn", "эстон", "таллин", " ee ", "[ee]", "-ee-") to "🇪🇪",
            listOf("lithuania", "vilnius", "литв", "вильнюс", " lt ", "[lt]", "-lt-") to "🇱🇹",
            listOf("kazakhstan", "almaty", "astana", "казах", "алмат", " kz ", "[kz]", "-kz-") to "🇰🇿",
            listOf("russia", "moscow", "росс", "москв", "спб", " ru ", "[ru]", "-ru-", ".ru") to "🇷🇺",
            listOf("japan", "tokyo", "япон", "токио", " jp ", "[jp]", "-jp-") to "🇯🇵",
            listOf("singapore", "сингапур", " sg ", "[sg]", "-sg-") to "🇸🇬",
            listOf("canada", "toronto", "канад", "торонто", " ca ", "[ca]", "-ca-") to "🇨🇦",
            listOf("uae", "dubai", "оаэ", "дубай", " ae ", "[ae]", "-ae-") to "🇦🇪",
            listOf("czech", "prague", "чех", "прага", " cz ", "[cz]", "-cz-") to "🇨🇿",
            listOf("italy", "milan", "итал", "милан", " it ", "[it]", "-it-") to "🇮🇹",
            listOf("spain", "madrid", "испан", "мадрид", " es ", "[es]", "-es-") to "🇪🇸",
            listOf("norway", "oslo", "норвег", "осло", " no ", "[no]", "-no-") to "🇳🇴",
            listOf("hong kong", "гонконг", " hk ", "[hk]", "-hk-") to "🇭🇰",
            listOf("korea", "seoul", "коре", "сеул", " kr ", "[kr]", "-kr-") to "🇰🇷"
        )
        for ((keywords, flag) in map) {
            if (keywords.any { combined.contains(it) }) return flag
        }
        val prefixMatch = Regex("^([A-Za-z]{2})[\\s_\\-|:]").find(remark.trim())
        if (prefixMatch != null) {
            val code = prefixMatch.groupValues[1].uppercase()
            if (code.all { it in 'A'..'Z' }) {
                val first = 0x1F1E6 + (code[0] - 'A')
                val second = 0x1F1E6 + (code[1] - 'A')
                return String(intArrayOf(first, second), 0, 2)
            }
        }
        return "🌐"
    }
}
