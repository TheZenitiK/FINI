package com.example.core.parser

import android.os.Build
import android.util.Base64
import com.example.BuildConfig
import com.example.core.logging.AppLogger
import com.example.core.network.SharedHttpClient
import com.example.data.model.ServerConfig
import com.example.data.model.Subscription
import com.example.data.model.SubscriptionExternalLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class FetchedSubscription(
    val subscription: Subscription,
    val servers: List<ServerConfig>
)

object ClientSpoofDiagnostics {
    fun buildUserAgent(happSpoofEnabled: Boolean): String {
        val osVer = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "14"
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        return if (happSpoofEnabled) {
            "Happ/4.10.1 (Android $osVer; $model)"
        } else {
            "FINI/${BuildConfig.VERSION_NAME} (Android; Xray-core)"
        }
    }

    fun buildHeaders(
        happSpoofEnabled: Boolean,
        sendDeviceHwid: Boolean,
        deviceHwid: String
    ): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        headers["User-Agent"] = buildUserAgent(happSpoofEnabled)
        if (sendDeviceHwid) {
            if (deviceHwid.isNotBlank()) {
                headers["x-hwid"] = deviceHwid
            }
            headers["x-device-os"] = "Android"
            headers["x-ver-os"] = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "14"
            headers["x-device-model"] = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        }
        return headers
    }
}

object SubscriptionFetcher {

    private val client: OkHttpClient by lazy {
        SharedHttpClient.baseClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val fallbackUserAgents = listOf(
        "v2rayNG/1.8.29",
        "Happ/4.10.1",
        "ClashMetaForAndroid/2.10"
    )

    private data class FetchAttemptResult(
        val servers: List<ServerConfig>,
        val profileTitle: String?,
        val userInfo: String,
        val externalLinks: List<SubscriptionExternalLink>
    )

    suspend fun fetch(
        url: String,
        existingSubscription: Subscription? = null,
        happSpoofEnabled: Boolean = false,
        sendDeviceHwid: Boolean = true,
        deviceHwid: String = ""
    ): Result<FetchedSubscription> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = url.trim()
            val subId = existingSubscription?.id ?: UUID.randomUUID().toString()
            AppLogger.log("SUB", "Загрузка подписки (${if (happSpoofEnabled) "Happ spoof" else "Default"}): $cleanUrl")

            var bestServers: List<ServerConfig> = emptyList()
            var bestHeaderTitle: String? = null
            var bestUserInfo = ""
            var bestLinks: List<SubscriptionExternalLink> = emptyList()
            var lastHttpError: Int? = null

            val primaryHeaders = ClientSpoofDiagnostics.buildHeaders(
                happSpoofEnabled = happSpoofEnabled,
                sendDeviceHwid = sendDeviceHwid,
                deviceHwid = deviceHwid
            )
            val primaryUa = primaryHeaders["User-Agent"]!!
            val userAgentsToTry = if (happSpoofEnabled) {
                listOf(primaryUa, "Happ/4.10.1", "v2rayNG/1.8.29")
            } else {
                (listOf(primaryUa) + fallbackUserAgents).distinct()
            }

            for (ua in userAgentsToTry) {
                val reqBuilder = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", ua)
                    .header("Accept", "*/*")

                if (sendDeviceHwid) {
                    if (deviceHwid.isNotBlank()) reqBuilder.header("x-hwid", deviceHwid)
                    reqBuilder.header("x-device-os", "Android")
                    reqBuilder.header("x-ver-os", Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "14")
                    reqBuilder.header("x-device-model", Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android")
                }

                val attempt = runCatching {
                    client.newCall(reqBuilder.build()).execute().use { response ->
                        if (!response.isSuccessful) {
                            lastHttpError = response.code
                            AppLogger.log("SUB", "HTTP ${response.code} для UA=$ua")
                            return@use null
                        }
                        val body = response.body?.string()?.trim().orEmpty()
                        if (body.isEmpty()) return@use null

                        val parsed = VlessUriParser.parseSubscriptionBody(body, subId)
                        if (parsed.isNotEmpty()) {
                            val headerLinks = parseExternalLinksFromHeaders(
                                supportUrl = response.header("support-url"),
                                webPageUrl = response.header("profile-web-page-url"),
                                announceUrl = response.header("announce-url"),
                                subscriptionUrl = cleanUrl
                            )
                            val commentLinks = parseExternalLinksFromBodyComments(body, cleanUrl)
                            val mergedLinks = (headerLinks + commentLinks)
                                .distinctBy { it.url.trim().lowercase() }
                            FetchAttemptResult(
                                servers = parsed,
                                profileTitle = response.header("profile-title"),
                                userInfo = response.header("subscription-userinfo") ?: "",
                                externalLinks = mergedLinks
                            )
                        } else null
                    }
                }.onFailure { err ->
                    AppLogger.log("SUB", "Сетевая ошибка для UA=$ua: ${err.localizedMessage ?: err.javaClass.simpleName}")
                }.getOrNull()

                if (attempt != null) {
                    bestServers = attempt.servers
                    bestHeaderTitle = attempt.profileTitle
                    bestUserInfo = attempt.userInfo
                    bestLinks = attempt.externalLinks
                    AppLogger.log(
                        "SUB",
                        "Подписка получена (UA=$ua): серверов=${bestServers.size}, ссылок=${bestLinks.size}"
                    )
                    break
                }
            }

            if (bestServers.isEmpty()) {
                val msg = if (lastHttpError != null) {
                    "Сервер подписки вернул HTTP $lastHttpError"
                } else {
                    "В подписке не найдено ни одной поддерживаемой конфигурации"
                }
                AppLogger.log("SUB", "Ошибка подписки: $msg")
                error(msg)
            }

            val parsedTitle = bestHeaderTitle?.let { decodeHeaderValue(it) }?.takeIf { it.isNotBlank() }

            val fallbackName = runCatching { URI(cleanUrl).host }.getOrNull() ?: "Подписка"
            val name = existingSubscription?.name ?: parsedTitle ?: fallbackName

            val upload = extractUserInfoValue(bestUserInfo, "upload")
            val download = extractUserInfoValue(bestUserInfo, "download")
            val total = extractUserInfoValue(bestUserInfo, "total")
            val expire = extractUserInfoValue(bestUserInfo, "expire")

            val subscription = Subscription(
                id = subId,
                name = name,
                url = cleanUrl,
                lastUpdatedAt = System.currentTimeMillis(),
                uploadBytes = upload,
                downloadBytes = download,
                totalBytes = total,
                expireTimestamp = expire,
                autoUpdateIntervalHours = existingSubscription?.autoUpdateIntervalHours ?: 0,
                autoUpdateOnLaunch = existingSubscription?.autoUpdateOnLaunch ?: false,
                pingOnLaunch = existingSubscription?.pingOnLaunch ?: false,
                externalLinks = bestLinks
            )
            FetchedSubscription(subscription, bestServers)
        }
    }

    internal fun parseExternalLinksFromHeaders(
        supportUrl: String?,
        webPageUrl: String?,
        announceUrl: String?,
        subscriptionUrl: String
    ): List<SubscriptionExternalLink> {
        val result = mutableListOf<SubscriptionExternalLink>()
        fun addIfValid(raw: String?, defaultLabel: String) {
            val decoded = raw?.let { decodeHeaderValue(it) }?.trim()?.takeIf { it.isNotBlank() } ?: return
            if (!isSafeExternalLink(decoded, subscriptionUrl)) return
            val label = inferLinkLabel(decoded, defaultLabel)
            result.add(SubscriptionExternalLink(label = label, url = decoded))
        }
        addIfValid(supportUrl, "Поддержка")
        addIfValid(webPageUrl, "Личный кабинет")
        addIfValid(announceUrl, "Объявления")
        return result.distinctBy { it.url.trim().lowercase() }
    }

    internal fun parseExternalLinksFromBodyComments(
        rawBody: String,
        subscriptionUrl: String
    ): List<SubscriptionExternalLink> {
        val textToScan = buildString {
            appendLine(rawBody)
            val decoded = VlessUriParser.tryDecodeBase64(rawBody)
            if (!decoded.isNullOrBlank()) appendLine(decoded)
        }
        val urlRegex = Regex("""(?:https?://|tg://|t\.me/)[^\s"'<>\]\)]+""", RegexOption.IGNORE_CASE)
        val links = mutableListOf<SubscriptionExternalLink>()
        textToScan.lines().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("#") && !trimmed.startsWith("//")) return@forEach
            urlRegex.findAll(trimmed).forEach { match ->
                val url = match.value.trimEnd('.', ',', ';')
                if (isSafeExternalLink(url, subscriptionUrl)) {
                    links.add(
                        SubscriptionExternalLink(
                            label = inferLinkLabel(url, "Ссылка"),
                            url = url
                        )
                    )
                }
            }
        }
        return links.distinctBy { it.url.trim().lowercase() }
    }

    private fun isSafeExternalLink(url: String, subscriptionUrl: String): Boolean {
        val lower = url.trim().lowercase()
        if (lower.isBlank()) return false
        if (lower == subscriptionUrl.trim().lowercase()) return false
        if (lower.startsWith("vless://") || lower.startsWith("vmess://") ||
            lower.startsWith("trojan://") || lower.startsWith("ss://") ||
            lower.startsWith("hysteria2://") || lower.startsWith("hy2://")
        ) return false
        return lower.startsWith("https://") ||
            lower.startsWith("http://") ||
            lower.startsWith("tg://") ||
            lower.startsWith("t.me/")
    }

    private fun inferLinkLabel(url: String, fallback: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("t.me/") || lower.startsWith("tg://") -> {
                if (lower.contains("bot") || fallback.contains("поддерж", true)) "Telegram / Поддержка"
                else "Telegram"
            }
            lower.contains("support") || lower.contains("help") -> "Поддержка"
            lower.contains("news") || lower.contains("announce") -> "Новости"
            else -> fallback
        }
    }

    private fun decodeHeaderValue(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("base64:", ignoreCase = true)) {
            runCatching {
                val b64 = trimmed.substringAfter(":")
                String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrDefault(trimmed)
        } else {
            runCatching { URLDecoder.decode(trimmed, "UTF-8") }.getOrDefault(trimmed)
        }
    }

    private fun extractUserInfoValue(header: String, key: String): Long {
        val regex = Regex("""$key\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(header)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    }
}
