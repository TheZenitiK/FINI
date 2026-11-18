package com.example.data.model

import kotlinx.serialization.Serializable

enum class VpnProtocol(val displayName: String) {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    HYSTERIA2("Hysteria 2")
}

enum class TransportType(val raw: String, val label: String) {
    TCP("tcp", "TCP"),
    XHTTP("xhttp", "Xhttp"),
    WS("ws", "WS"),
    GRPC("grpc", "gRPC"),
    HTTPUPGRADE("httpupgrade", "HTTPUpgrade")
}

enum class SecurityType(val raw: String, val label: String) {
    NONE("none", "NONE"),
    TLS("tls", "TLS"),
    REALITY("reality", "REALITY")
}

@Serializable
data class ServerConfig(
    val id: String,
    val name: String,
    val countryFlag: String,
    val protocol: VpnProtocol = VpnProtocol.VLESS,
    val transport: TransportType = TransportType.TCP,
    val security: SecurityType = SecurityType.REALITY,
    val host: String,
    val port: Int,
    val uuidOrPassword: String,
    val flow: String = "",
    val encryption: String = "none",
    val sni: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val path: String = "/",
    val hostHeader: String = "",
    val xhttpMode: String = "auto",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val rawUri: String,
    val subscriptionId: String? = null,
    val isFavorite: Boolean = false,
    val favoriteOrder: Int = 0,
    val isAutoSelect: Boolean = false,
    val sortOrder: Int = 0,
    val pingMs: Int? = null,
    val pingFailed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val obfs: String = "",
    val obfsPassword: String = "",
    val pinSha256: String = "",
    val upMbps: Int = 0,
    val downMbps: Int = 0,
    val ports: String = "",
    val customJsonConfig: String = ""
) {
    val subProtocolLabel: String
        get() = if (customJsonConfig.isNotBlank()) {
            "JSON"
        } else if (isAutoSelect) {
            "AUTO"
        } else if (protocol == VpnProtocol.HYSTERIA2) {
            if (obfs.equals("salamander", ignoreCase = true)) "QUIC • Salamander" else "QUIC"
        } else buildString {
            when (security) {
                SecurityType.REALITY -> {
                    append("REALITY")
                    if (transport == TransportType.XHTTP) append(" • Xhttp")
                    else if (transport == TransportType.GRPC) append(" • gRPC")
                }
                SecurityType.TLS -> {
                    if (transport == TransportType.XHTTP) {
                        append("Xhttp")
                    } else if (flow.contains("vision", ignoreCase = true)) {
                        append("XTLS-VISION")
                    } else {
                        append("TLS • ${transport.label}")
                    }
                }
                SecurityType.NONE -> append(transport.label)
            }
        }
}

@Serializable
data class SubscriptionExternalLink(
    val label: String,
    val url: String
)

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val lastUpdatedAt: Long,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireTimestamp: Long = 0L,
    val autoUpdateIntervalHours: Int = 0,
    val autoUpdateOnLaunch: Boolean = false,
    val pingOnLaunch: Boolean = false,
    val externalLinks: List<SubscriptionExternalLink> = emptyList()
) {
    val usedBytes: Long get() = (uploadBytes + downloadBytes).coerceAtLeast(0L)
    val remainingBytes: Long get() = if (totalBytes > 0L) (totalBytes - usedBytes).coerceAtLeast(0L) else 0L

    fun normalizedExpireMillis(): Long {
        if (expireTimestamp <= 0L) return 0L
        return if (expireTimestamp < 10_000_000_000L) expireTimestamp * 1000L else expireTimestamp
    }
}

fun formatSubscriptionRemainingTime(
    expireTimestamp: Long,
    isRussian: Boolean,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (expireTimestamp <= 0L) {
        return if (isRussian) "Без срока" else "No expiration"
    }
    val expireMs = if (expireTimestamp < 10_000_000_000L) expireTimestamp * 1000L else expireTimestamp
    val diffMs = expireMs - nowMs
    if (diffMs <= 0L) {
        return if (isRussian) "Истекла" else "Expired"
    }
    val totalMinutes = diffMs / 60_000L
    val days = totalMinutes / (60L * 24L)
    val hours = (totalMinutes % (60L * 24L)) / 60L
    val minutes = totalMinutes % 60L
    return when {
        days > 0L && hours > 0L -> if (isRussian) "$days дн. $hours ч." else "${days}d ${hours}h"
        days > 0L -> if (isRussian) "$days дн." else "${days}d"
        hours > 0L -> if (isRussian) "$hours ч. $minutes мин." else "${hours}h ${minutes}m"
        else -> if (isRussian) "${minutes.coerceAtLeast(1L)} мин." else "${minutes.coerceAtLeast(1L)}m"
    }
}

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

enum class ActiveNetworkType {
    WIFI,
    MOBILE,
    OTHER
}

enum class PingMethod {
    TCP,
    HTTP_GET,
    ICMP
}

enum class PingDisplayMode {
    MILLISECONDS,
    DOTS
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

enum class AppIconStyle {
    DARK,
    WHITE
}

enum class AppLanguage {
    SYSTEM,
    RUSSIAN,
    ENGLISH
}

enum class AppScalePreset(val stepIndex: Int, val factor: Float) {
    COMPACT(0, 0.85f),
    SMALL(1, 0.92f),
    NORMAL(2, 1.00f),
    LARGE(3, 1.10f),
    LARGER(4, 1.20f);

    companion object {
        fun fromStep(step: Int): AppScalePreset =
            entries.firstOrNull { it.stepIndex == step } ?: NORMAL
    }
}

enum class AccentPreset(
    val primaryHex: Long,
    val darkPrimaryHex: Long,
    val lightContainerHex: Long,
    val darkContainerHex: Long
) {
    CYAN(0xFF0099CC, 0xFF00D8FF, 0xFFD9F5FF, 0xFF063142),
    EMERALD(0xFF059669, 0xFF10B981, 0xFFD1FAE5, 0xFF063626),
    INDIGO(0xFF4F46E5, 0xFF818CF8, 0xFFE0E7FF, 0xFF1F1D4D),
    CORAL(0xFFEA580C, 0xFFFB923C, 0xFFFFEDD5, 0xFF431A06),
    AMBER(0xFFD97706, 0xFFFBBF24, 0xFFFEF3C7, 0xFF3F2604),
    CRIMSON(0xFFDC2626, 0xFFF87171, 0xFFFEE2E2, 0xFF450E0E)
}

enum class AppRoutingMode {
    ONLY_SELECTED,
    BYPASS
}

data class AutoDisconnectConfig(
    val enabled: Boolean = false,
    val startHour: Int = 1,
    val startMinute: Int = 0,
    val endHour: Int = 8,
    val endMinute: Int = 0,
    val inactivityMinutes: Int = 2
)

enum class UpdateCheckInterval(val hours: Int) {
    HOURS_6(6),
    HOURS_12(12),
    HOURS_24(24),
    HOURS_48(48)
}

data class AutoUpdateConfig(
    val autoUpdateEnabled: Boolean = false,
    val autoCheckOnLaunch: Boolean = true,
    val checkInterval: UpdateCheckInterval = UpdateCheckInterval.HOURS_24,
    val lastCheckTimestamp: Long = 0L
)

@Serializable
data class XrayCoreProfile(
    val id: String,
    val versionTag: String,
    val title: String,
    val isBuiltIn: Boolean = false,
    val releaseNotes: String = "",
    val downloadedAt: Long = 0L
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false
)

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val accentPreset: AccentPreset = AccentPreset.CYAN,
    val appIconStyle: AppIconStyle = AppIconStyle.DARK,
    val pingMethod: PingMethod = PingMethod.TCP,
    val pingDisplayMode: PingDisplayMode = PingDisplayMode.MILLISECONDS,
    val bypassLan: Boolean = true,
    val bypassRussianDomains: Boolean = false,
    val bypassMincifry: Boolean = false,
    val customRoutesEnabled: Boolean = false,
    val customDirectDomains: Set<String> = emptySet(),
    val perAppEnabled: Boolean = false,
    val splitAppRulesByNetwork: Boolean = false,
    val unifiedAppMode: AppRoutingMode = AppRoutingMode.BYPASS,
    val unifiedAppPackages: Set<String> = emptySet(),
    val wifiAppMode: AppRoutingMode = AppRoutingMode.BYPASS,
    val wifiAppPackages: Set<String> = emptySet(),
    val mobileAppMode: AppRoutingMode = AppRoutingMode.BYPASS,
    val mobileAppPackages: Set<String> = emptySet(),
    val autoSwitchServerByNetwork: Boolean = false,
    val networkAutoSwitchActive: Boolean = false,
    val wifiServerId: String? = null,
    val mobileServerId: String? = null,
    val autoConnectOnLaunch: Boolean = false,
    val autoConnectOnMobile: Boolean = false,
    val disconnectOnWifi: Boolean = false,
    val showVpnStatusNotification: Boolean = true,
    val showSpeedInNotification: Boolean = false,
    val autoDisconnect: AutoDisconnectConfig = AutoDisconnectConfig(),
    val autoUpdate: AutoUpdateConfig = AutoUpdateConfig(),
    val selectedCoreId: String = "builtin",
    val savedCoreProfiles: List<XrayCoreProfile> = emptyList(),
    val collapsedSubscriptionIds: Set<String> = emptySet(),
    val selectedServerId: String? = null,
    val appScalePreset: AppScalePreset = AppScalePreset.NORMAL,
    val seasonalSnowEnabled: Boolean = true,
    val pinConnectCard: Boolean = false,
    val happSpoofEnabled: Boolean = false,
    val sendDeviceHwid: Boolean = true,
    val deviceHwid: String = "",
    val logsEnabled: Boolean = false,
    val experimentalModeEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val onlyAvailableServers: Boolean = false,
    val sortByPingEnabled: Boolean = false
)
