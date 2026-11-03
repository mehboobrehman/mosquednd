package com.mosquednd.net

import com.mosquednd.util.AgentLogger
import com.mosquednd.util.RedactionUtils
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * NetworkLoggingInterceptor: Intercepts HTTP requests and responses, applying RedactionUtils
 * systematically to headers, query parameters, and bodies, and logging metadata and timings via AgentLogger.
 */
class NetworkLoggingInterceptor : Interceptor {
    private val utf8 = Charset.forName("UTF-8")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.nanoTime()

        // Feature Flag: If force401Error is enabled and this is not an API config call, simulate 401 Unauthorized
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

        // Redact URL and query parameters
        val redactedUrl = RedactionUtils.redact(request.url.toString())

        // Redact request headers
        val requestHeadersMap = mutableMapOf<String, String>()
        for (name in request.headers.names()) {
            val value = request.header(name) ?: ""
            requestHeadersMap[name] = if (name.equals("Authorization", true) || name.equals("Cookie", true) || name.equals("Api-Key", true)) {
                "[REDACTED]"
            } else {
                RedactionUtils.redact(value)
            }
        }

        // Read request body
        var requestBodyString = ""
        val requestBody = request.body
        if (requestBody != null) {
            try {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                val contentType = requestBody.contentType()
                val charset = contentType?.charset(utf8) ?: utf8
                val rawBody = buffer.readString(charset)
                requestBodyString = RedactionUtils.redact(rawBody)
            } catch (e: Exception) {
                requestBodyString = "[Failed to read request body: ${e.message}]"
            }
        }

        val requestLogMsg = "--> ${request.method} $redactedUrl"
        AgentLogger.i(requestLogMsg, "NetworkLog")

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
            val errorMsg = "<-- HTTP FAILED [${durationMs}ms] ${request.method} $redactedUrl: ${e.message}"
            AgentLogger.e(errorMsg, e, "NetworkLog")
            
            AgentLogger.emitEnvelope("log", mapOf(
                "level" to "ERROR",
                "message" to errorMsg,
                "tag" to "NetworkLog",
                "metrics" to mapOf(
                    "method" to request.method,
                    "url" to redactedUrl,
                    "durationMs" to durationMs,
                    "error" to (e.message ?: "Unknown error")
                )
            ))
            throw e
        }

        val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)

        // Redact response headers
        val responseHeadersMap = mutableMapOf<String, String>()
        for (name in response.headers.names()) {
            val value = response.header(name) ?: ""
            responseHeadersMap[name] = if (name.equals("Set-Cookie", true) || name.equals("Authorization", true)) {
                "[REDACTED]"
            } else {
                RedactionUtils.redact(value)
            }
        }

        // Read response body safely
        var responseBodyString = ""
        val responseBody = response.body
        if (responseBody != null) {
            try {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer.clone()
                val contentType = responseBody.contentType()
                val charset = contentType?.charset(utf8) ?: utf8
                val rawBody = buffer.readString(charset)
                responseBodyString = RedactionUtils.redact(rawBody)
            } catch (e: Exception) {
                responseBodyString = "[Failed to read response body: ${e.message}]"
            }
        }

        val contentLength = responseBody?.contentLength() ?: -1L
        val responseLogMsg = "<-- ${response.code} ${response.message} $redactedUrl (${durationMs}ms, ${contentLength} bytes)"

        if (response.isSuccessful) {
            AgentLogger.i(responseLogMsg, "NetworkLog")
        } else {
            AgentLogger.w(responseLogMsg, "NetworkLog")
            if (responseBodyString.isNotBlank()) {
                AgentLogger.w("<-- ERROR BODY: $responseBodyString", "NetworkLog")
            }
        }

        // Emit structured network telemetry metric
        AgentLogger.emitEnvelope("log", mapOf(
            "level" to if (response.isSuccessful) "INFO" else "WARN",
            "message" to responseLogMsg,
            "tag" to "NetworkLog",
            "metrics" to mapOf(
                "method" to request.method,
                "url" to redactedUrl,
                "statusCode" to response.code,
                "durationMs" to durationMs,
                "contentLength" to contentLength,
                "requestHeaders" to requestHeadersMap,
                "responseHeaders" to responseHeadersMap,
                "requestBody" to requestBodyString,
                "responseBody" to responseBodyString
            )
        ))

        return response
    }
}
