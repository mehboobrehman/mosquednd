package com.mosquednd.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Release version of NetworkClientProvider without network logging interceptors.
 */
object NetworkClientProvider {
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
