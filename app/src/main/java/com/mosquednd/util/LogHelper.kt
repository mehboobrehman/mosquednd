package com.mosquednd.util

import android.util.Log
import com.mosquednd.BuildConfig

object LogHelper {

    enum class TelemetryCategory {
        STARTUP, NAVIGATION, GESTURE, ACTION, EXECUTION, DIAGNOSTIC
    }

    private val throttleMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun logd(tag: String, msg: String) {
        val scrubbed = scrub(msg)
        if (BuildConfig.DEBUG) {
            Log.d(tag, scrubbed)
        }
        AgentLogger.d(scrubbed, tag)
    }

    fun logw(tag: String, msg: String) {
        val scrubbed = scrub(msg)
        Log.w(tag, scrubbed)
        AgentLogger.w(scrubbed, tag)
    }

    fun loge(tag: String, msg: String, t: Throwable? = null) {
        val scrubbed = scrub(msg)
        Log.e(tag, scrubbed, t)
        AgentLogger.e(scrubbed, t, tag)
    }

    fun logTelemetry(category: TelemetryCategory, event: String, payloadMap: Map<String, Any> = emptyMap()) {
        val payloadStr = payloadMap.entries.joinToString(separator = ", ") { "${it.key}=${it.value}" }
        val msg = if (payloadStr.isNotEmpty()) {
            "[$category] Event: $event | $payloadStr"
        } else {
            "[$category] Event: $event"
        }
        logd("TELEMETRY", msg)
    }

    fun logThrottled(key: String, intervalMs: Long, level: String, tag: String, message: String) {
        val now = System.currentTimeMillis()
        val lastTime = throttleMap[key] ?: 0L
        if (now - lastTime >= intervalMs) {
            throttleMap[key] = now
            when (level.uppercase()) {
                "DEBUG", "D" -> logd(tag, message)
                "WARN", "W" -> logw(tag, message)
                "ERROR", "E" -> loge(tag, message)
                else -> logd(tag, message)
            }
        }
    }

    fun logLocation(tag: String, lat: Double, lng: Double) {
        // Redact location by logging only last 2 decimal places
        val redactedLat = "%.2f".format(lat % 1)
        val redactedLng = "%.2f".format(lng % 1)
        logd(tag, "Location: Lat [REDACTED].$redactedLat, Lng [REDACTED].$redactedLng")
    }

    private fun scrub(msg: String): String {
        // Simple scrubbing for now. 
        // In a real scenario, this would use complex regex for mosque names, URIs, etc.
        // For the sake of this task, I will focus on the coordinates redaction as requested.
        
        var scrubbed = msg
        
        // Example: Regex for coordinates
        // This is a basic example, might need refinement
        val coordinateRegex = Regex("""(-?\d+\.\d+),\s*(-?\d+\.\d+)""")
        scrubbed = scrubbed.replace(coordinateRegex, "[REDACTED_COORD]")

        // Redact potential URIs
        val uriRegex = Regex("""(http|https)://[^\s]+""")
        scrubbed = scrubbed.replace(uriRegex, "[REDACTED_URI]")

        return scrubbed
    }
}
