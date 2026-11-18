package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY lastUpdatedAt DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET name = :newName WHERE id = :id")
    suspend fun rename(id: String, newName: String)

    @Query(
        "UPDATE subscriptions SET name = :newName, url = :newUrl, " +
            "autoUpdateIntervalHours = :intervalHours, autoUpdateOnLaunch = :onLaunch, " +
            "pingOnLaunch = :pingOnLaunch WHERE id = :id"
    )
    suspend fun updateSettings(
        id: String,
        newName: String,
        newUrl: String,
        intervalHours: Int,
        onLaunch: Boolean,
        pingOnLaunch: Boolean
    )

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAll()
}

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY isFavorite DESC, favoriteOrder ASC, sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY isFavorite DESC, favoriteOrder ASC, sortOrder ASC, createdAt ASC")
    suspend fun getAllOnce(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE subscriptionId = :subscriptionId ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getBySubscriptionOnce(subscriptionId: String): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: ServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(servers: List<ServerEntity>)

    @Query("UPDATE servers SET isFavorite = :favorite, favoriteOrder = :favoriteOrder WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, favoriteOrder: Int)

    @Query("UPDATE servers SET favoriteOrder = :favoriteOrder WHERE id = :id")
    suspend fun updateFavoriteOrder(id: String, favoriteOrder: Int)

    @Query("SELECT COALESCE(MAX(favoriteOrder), -1) FROM servers WHERE isFavorite = 1")
    suspend fun getMaxFavoriteOrder(): Int

    @Query("UPDATE servers SET pingMs = :pingMs, pingFailed = :failed WHERE id = :id")
    suspend fun updatePing(id: String, pingMs: Int?, failed: Boolean)

    @Query("UPDATE servers SET pingMs = NULL, pingFailed = 0")
    suspend fun clearAllPings()

    @Query("UPDATE servers SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("UPDATE servers SET customJsonConfig = :customJsonConfig WHERE id = :id")
    suspend fun updateCustomJson(id: String, customJsonConfig: String)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM servers WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: String)

    @Query("DELETE FROM servers")
    suspend fun deleteAll()
}
