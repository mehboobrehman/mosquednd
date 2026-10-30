package com.mosquednd.net

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Release stub for NetworkLoggingInterceptor.
 */
class NetworkLoggingInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
