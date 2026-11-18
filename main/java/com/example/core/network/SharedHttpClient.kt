package com.example.core.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttp base instance so HTTP features (subscriptions, GitHub updates, HTTP ping)
 * reuse a single Dispatcher and ConnectionPool instead of spawning separate thread pools.
 */
object SharedHttpClient {
    val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(6, 3, TimeUnit.MINUTES))
            .build()
    }
}
