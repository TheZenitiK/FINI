package com.example.data.repository

import com.example.data.local.ServerDao
import com.example.data.local.ServerEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.SubscriptionDao
import com.example.data.local.SubscriptionEntity
import com.example.data.model.AppSettings
import com.example.data.model.ServerConfig
import com.example.data.model.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VpnRepository(
    private val serverDao: ServerDao,
    private val subscriptionDao: SubscriptionDao,
    val settingsStore: SettingsDataStore
) {
    val serversFlow: Flow<List<ServerConfig>> = serverDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val subscriptionsFlow: Flow<List<Subscription>> = subscriptionDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow

    suspend fun getAllServersOnce(): List<ServerConfig> =
        serverDao.getAllOnce().map { it.toDomain() }

    suspend fun getServersBySubscriptionOnce(subscriptionId: String): List<ServerConfig> =
        serverDao.getBySubscriptionOnce(subscriptionId).map { it.toDomain() }

    suspend fun getServerById(id: String): ServerConfig? =
        serverDao.getById(id)?.toDomain()

    suspend fun getSubscriptionById(id: String): Subscription? =
        subscriptionDao.getById(id)?.toDomain()

    suspend fun getSubscriptionByUrl(url: String): Subscription? =
        subscriptionDao.getByUrl(url.trim())?.toDomain()

    suspend fun saveStandaloneServer(server: ServerConfig) {
        saveStandaloneServers(listOf(server))
    }

    suspend fun saveStandaloneServers(servers: List<ServerConfig>) {
        if (servers.isEmpty()) return
        val existingStandalone = serverDao.getAllOnce()
            .filter { it.subscriptionId == null }
            .map { it.toDomain() }
        val existingKeys = existingStandalone.map { canonicalServerKey(it) }.toMutableSet()
        val toInsert = mutableListOf<ServerEntity>()
        servers.forEach { srv ->
            val key = canonicalServerKey(srv)
            if (existingKeys.add(key)) {
                toInsert.add(ServerEntity.fromDomain(srv))
            }
        }
        if (toInsert.isNotEmpty()) {
            serverDao.upsertAll(toInsert)
        }
    }

    private fun canonicalServerKey(srv: ServerConfig): String {
        return if (srv.customJsonConfig.isNotBlank()) {
            "JSON|${srv.name.trim().lowercase()}|${srv.customJsonConfig.hashCode()}"
        } else if (srv.isAutoSelect) {
            "AUTO|${srv.name.trim().lowercase()}"
        } else {
            listOf(
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
    }

    suspend fun saveSubscriptionWithServers(subscription: Subscription, servers: List<ServerConfig>) {
        subscriptionDao.upsert(SubscriptionEntity.fromDomain(subscription))
        serverDao.deleteBySubscriptionId(subscription.id)
        serverDao.upsertAll(servers.map { ServerEntity.fromDomain(it) })
    }

    suspend fun renameSubscription(subscriptionId: String, newName: String) {
        subscriptionDao.rename(subscriptionId, newName.trim())
    }

    suspend fun updateSubscriptionSettings(
        subscriptionId: String,
        newName: String,
        newUrl: String,
        autoUpdateIntervalHours: Int,
        autoUpdateOnLaunch: Boolean,
        pingOnLaunch: Boolean
    ) {
        subscriptionDao.updateSettings(
            id = subscriptionId,
            newName = newName.trim(),
            newUrl = newUrl.trim(),
            intervalHours = autoUpdateIntervalHours,
            onLaunch = autoUpdateOnLaunch,
            pingOnLaunch = pingOnLaunch
        )
    }

    suspend fun deleteSubscription(subscriptionId: String) {
        serverDao.deleteBySubscriptionId(subscriptionId)
        subscriptionDao.deleteById(subscriptionId)
    }

    suspend fun deleteServer(serverId: String) {
        serverDao.deleteById(serverId)
    }

    suspend fun updateServerCustomJson(serverId: String, customJsonConfig: String) {
        serverDao.updateCustomJson(serverId, customJsonConfig)
    }

    suspend fun toggleFavorite(serverId: String, isFavorite: Boolean) {
        val nextOrder = if (isFavorite) serverDao.getMaxFavoriteOrder() + 1 else 0
        serverDao.setFavorite(serverId, isFavorite, nextOrder)
    }

    suspend fun reorderFavorites(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            serverDao.updateFavoriteOrder(id, index)
        }
    }

    suspend fun updatePingResult(serverId: String, pingMs: Int?, failed: Boolean) {
        serverDao.updatePing(serverId, pingMs, failed)
    }

    suspend fun clearAllPingResults() {
        serverDao.clearAllPings()
    }

    suspend fun sortServersByPing() {
        val all = serverDao.getAllOnce().map { it.toDomain() }
        if (all.isEmpty()) return

        val pingComparator = Comparator<ServerConfig> { a, b ->
            val aAvail = a.pingMs != null && !a.pingFailed
            val bAvail = b.pingMs != null && !b.pingFailed
            when {
                aAvail && bAvail -> a.pingMs!!.compareTo(b.pingMs!!)
                aAvail -> -1
                bAvail -> 1
                !a.pingFailed && b.pingFailed -> -1
                a.pingFailed && !b.pingFailed -> 1
                else -> a.sortOrder.compareTo(b.sortOrder)
            }
        }

        val favoritesSorted = all.filter { it.isFavorite }.sortedWith(pingComparator)
        favoritesSorted.forEachIndexed { idx, srv ->
            serverDao.updateFavoriteOrder(srv.id, idx)
        }

        val bySub = all.groupBy { it.subscriptionId }
        bySub.forEach { (_, list) ->
            list.sortedWith(pingComparator).forEachIndexed { idx, srv ->
                serverDao.updateSortOrder(srv.id, idx)
            }
        }
    }

    suspend fun deleteAllServersAndSubscriptions() {
        serverDao.deleteAll()
        subscriptionDao.deleteAll()
        settingsStore.clearServerReferences()
    }
}
