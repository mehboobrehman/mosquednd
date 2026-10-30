package com.mosquednd.net

import com.mosquednd.util.AgentLogger
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * NetworkLoggingInterceptor logs HTTP request and response headers, status codes,
 * execution time, and errors via AgentLogger for remote telemetry and debugging.
 */
class NetworkLoggingInterceptor : Interceptor {
    private val utf8 = Charset.forName("UTF-8")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.nanoTime()

        // Feature Flag: If force401Error is enabled and this is an API call, simulate 401 Unauthorized
        if (com.mosquednd.config.RemoteConfigManager.force401Error && !request.url.encodedPath.contains("/api/config")) {
            AgentLogger.w("⚠️ [FeatureFlag] force401Error active — intercepting request to simulate 401 Unauthorized: ${request.url}", "NetworkLog")
            return okhttp3.Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized (Simulated by RemoteConfig Feature Flag)")
                .body("{\"error\": \"Unauthorized simulated by feature flag\"}".toResponseBody("application/json".toMediaTypeOrNull()))
                .sentRequestAtMillis(System.currentTimeMillis())
                .receivedResponseAtMillis(System.currentTimeMillis())
                .build()
        }

        val connection = chain.connection()
        val requestLog = buildString {
            append("--> ")
            append(request.method)
            append(' ')
            append(request.url)
            if (connection != null) {
                append(" (")
                append(connection.protocol())
                append(")")
            }
        }
        AgentLogger.i(requestLog, "NetworkLog")

        // Log request headers/body if needed
        val requestBody = request.body
        if (requestBody != null) {
            try {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                val contentType = requestBody.contentType()
                val charset = contentType?.charset(utf8) ?: utf8
                val bodyString = buffer.readString(charset)
                if (bodyString.isNotBlank()) {
                    AgentLogger.d("--> REQUEST BODY: $bodyString", "NetworkLog")
                }
            } catch (e: Exception) {
                AgentLogger.w("--> Failed to read request body: ${e.message}", "NetworkLog")
            }
        }

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
            AgentLogger.e("<-- HTTP FAILED [${durationMs}ms] ${request.method} ${request.url}: ${e.message}", e, "NetworkLog")
            throw e
        }

        val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
        val responseBody = response.body
        val contentLength = responseBody?.contentLength() ?: -1L
        val bodySize = if (contentLength != -1L) "$contentLength-byte" else "unknown-length"

        val responseLog = buildString {
            append("<-- ")
            append(response.code)
            if (response.message.isNotEmpty()) {
                append(' ')
                append(response.message)
            }
            append(' ')
            append(response.request.url)
            append(" (")
            append(durationMs)
            append("ms, ")
            append(bodySize)
            append(" body)")
        }

        if (response.isSuccessful) {
            AgentLogger.i(responseLog, "NetworkLog")
        } else {
            AgentLogger.w(responseLog, "NetworkLog")
            // Log error body if present for non-successful responses
            try {
                if (responseBody != null) {
                    val source = responseBody.source()
                    source.request(Long.MAX_VALUE) // Buffer the entire body
                    val buffer = source.buffer.clone()
                    val contentType = responseBody.contentType()
                    val charset = contentType?.charset(utf8) ?: utf8
                    val errorBodyString = buffer.readString(charset)
                    if (errorBodyString.isNotBlank()) {
                        AgentLogger.w("<-- ERROR BODY: $errorBodyString", "NetworkLog")
                    }
                }
            } catch (e: Exception) {
                AgentLogger.w("<-- Failed to read error body: ${e.message}", "NetworkLog")
            }
        }

        return response
    }
}
