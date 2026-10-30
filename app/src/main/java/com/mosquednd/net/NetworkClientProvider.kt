package com.mosquednd.net

import com.mosquednd.util.AgentLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Provides configured OkHttpClient instances equipped with NetworkLoggingInterceptor
 * for API calls (e.g. Google Drive sync, OAuth API calls, telemetry).
 */
object NetworkClientProvider {

    /**
     * Shared OkHttpClient configured with NetworkLoggingInterceptor and sensible timeouts.
     */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(NetworkLoggingInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
