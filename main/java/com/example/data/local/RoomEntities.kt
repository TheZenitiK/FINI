package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.SecurityType
import com.example.data.model.ServerConfig
import com.example.data.model.Subscription
import com.example.data.model.SubscriptionExternalLink
import com.example.data.model.TransportType
import com.example.data.model.VpnProtocol
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val roomJson = Json { ignoreUnknownKeys = true }

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
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
    val externalLinksJson: String = ""
) {
    fun toDomain(): Subscription {
        val links = if (externalLinksJson.isBlank()) {
            emptyList()
        } else {
            runCatching { roomJson.decodeFromString<List<SubscriptionExternalLink>>(externalLinksJson) }
                .getOrDefault(emptyList())
        }
        return Subscription(
            id = id,
            name = name,
            url = url,
            lastUpdatedAt = lastUpdatedAt,
            uploadBytes = uploadBytes,
            downloadBytes = downloadBytes,
            totalBytes = totalBytes,
            expireTimestamp = expireTimestamp,
            autoUpdateIntervalHours = autoUpdateIntervalHours,
            autoUpdateOnLaunch = autoUpdateOnLaunch,
            pingOnLaunch = pingOnLaunch,
            externalLinks = links
        )
    }

    companion object {
        fun fromDomain(sub: Subscription): SubscriptionEntity = SubscriptionEntity(
            id = sub.id,
            name = sub.name,
            url = sub.url,
            lastUpdatedAt = sub.lastUpdatedAt,
            uploadBytes = sub.uploadBytes,
            downloadBytes = sub.downloadBytes,
            totalBytes = sub.totalBytes,
            expireTimestamp = sub.expireTimestamp,
            autoUpdateIntervalHours = sub.autoUpdateIntervalHours,
            autoUpdateOnLaunch = sub.autoUpdateOnLaunch,
            pingOnLaunch = sub.pingOnLaunch,
            externalLinksJson = if (sub.externalLinks.isEmpty()) "" else roomJson.encodeToString(sub.externalLinks)
        )
    }
}

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val countryFlag: String,
    val protocol: String,
    val transport: String,
    val security: String,
    val host: String,
    val port: Int,
    val uuidOrPassword: String,
    val flow: String,
    val encryption: String,
    val sni: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val spiderX: String,
    val path: String,
    val hostHeader: String,
    val xhttpMode: String,
    val alpn: String,
    val allowInsecure: Boolean,
    val rawUri: String,
    val subscriptionId: String?,
    val isFavorite: Boolean,
    val favoriteOrder: Int = 0,
    val isAutoSelect: Boolean = false,
    val sortOrder: Int = 0,
    val pingMs: Int?,
    val pingFailed: Boolean,
    val createdAt: Long,
    val note: String = "",
    val obfs: String = "",
    val obfsPassword: String = "",
    val pinSha256: String = "",
    val upMbps: Int = 0,
    val downMbps: Int = 0,
    val ports: String = "",
    val customJsonConfig: String = ""
) {
    fun toDomain(): ServerConfig = ServerConfig(
        id = id,
        name = name,
        countryFlag = countryFlag,
        protocol = runCatching { VpnProtocol.valueOf(protocol) }.getOrDefault(VpnProtocol.VLESS),
        transport = runCatching { TransportType.valueOf(transport) }.getOrDefault(TransportType.TCP),
        security = runCatching { SecurityType.valueOf(security) }.getOrDefault(SecurityType.REALITY),
        host = host,
        port = port,
        uuidOrPassword = uuidOrPassword,
        flow = flow,
        encryption = encryption,
        sni = sni,
        fingerprint = fingerprint,
        publicKey = publicKey,
        shortId = shortId,
        spiderX = spiderX,
        path = path,
        hostHeader = hostHeader,
        xhttpMode = xhttpMode,
        alpn = alpn,
        allowInsecure = allowInsecure,
        rawUri = rawUri,
        subscriptionId = subscriptionId,
        isFavorite = isFavorite,
        favoriteOrder = favoriteOrder,
        isAutoSelect = isAutoSelect,
        sortOrder = sortOrder,
        pingMs = pingMs,
        pingFailed = pingFailed,
        createdAt = createdAt,
        note = note,
        obfs = obfs,
        obfsPassword = obfsPassword,
        pinSha256 = pinSha256,
        upMbps = upMbps,
        downMbps = downMbps,
        ports = ports,
        customJsonConfig = customJsonConfig
    )

    companion object {
        fun fromDomain(server: ServerConfig): ServerEntity = ServerEntity(
            id = server.id,
            name = server.name,
            countryFlag = server.countryFlag,
            protocol = server.protocol.name,
            transport = server.transport.name,
            security = server.security.name,
            host = server.host,
            port = server.port,
            uuidOrPassword = server.uuidOrPassword,
            flow = server.flow,
            encryption = server.encryption,
            sni = server.sni,
            fingerprint = server.fingerprint,
            publicKey = server.publicKey,
            shortId = server.shortId,
            spiderX = server.spiderX,
            path = server.path,
            hostHeader = server.hostHeader,
            xhttpMode = server.xhttpMode,
            alpn = server.alpn,
            allowInsecure = server.allowInsecure,
            rawUri = server.rawUri,
            subscriptionId = server.subscriptionId,
            isFavorite = server.isFavorite,
            favoriteOrder = server.favoriteOrder,
            isAutoSelect = server.isAutoSelect,
            sortOrder = server.sortOrder,
            pingMs = server.pingMs,
            pingFailed = server.pingFailed,
            createdAt = server.createdAt,
            note = server.note,
            obfs = server.obfs,
            obfsPassword = server.obfsPassword,
            pinSha256 = server.pinSha256,
            upMbps = server.upMbps,
            downMbps = server.downMbps,
            ports = server.ports,
            customJsonConfig = server.customJsonConfig
        )
    }
}
