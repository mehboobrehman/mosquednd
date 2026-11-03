package com.mosquednd.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * PerformanceSampler: Periodically samples system performance metrics (memory, threads, battery, startup/uptime)
 * in debug builds and emits telemetry metric envelopes.
 */
object PerformanceSampler {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "AgentLogger-PerformanceSampler").apply { isDaemon = true }
    }
    private var isRunning = false

    fun start(context: Context, intervalSeconds: Long = 30L) {
        if (isRunning) return
        isRunning = true

        scheduler.scheduleAtFixedRate({
            try {
                collectAndEmit(context)
            } catch (e: Exception) {
                AgentLogger.w("Failed to collect performance metrics: ${e.message}", "PerformanceSampler")
            }
        }, 5L, intervalSeconds, TimeUnit.SECONDS)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            scheduler.shutdown()
        } catch (_: Exception) {}
    }

    private fun collectAndEmit(context: Context) {
        val runtime = Runtime.getRuntime()
        val totalMemMB = runtime.totalMemory() / (1024 * 1024)
        val freeMemMB = runtime.freeMemory() / (1024 * 1024)
        val usedMemMB = totalMemMB - freeMemMB
        val maxMemMB = runtime.maxMemory() / (1024 * 1024)
        val nativeHeapMB = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val threadCount = Thread.activeCount()
        val uptimeMs = SystemClock.elapsedRealtime()

        // Battery state
        var batteryLevel = -1
        var isCharging = false
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, filter)
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryLevel = (level * 100) / scale
                }
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) {}

        val metricsMap = mapOf(
            "totalMemoryMB" to totalMemMB,
            "freeMemoryMB" to freeMemMB,
            "usedMemoryMB" to usedMemMB,
            "maxMemoryMB" to maxMemMB,
            "nativeHeapAllocatedMB" to nativeHeapMB,
            "threadCount" to threadCount,
            "uptimeMs" to uptimeMs,
            "batteryLevelPercent" to batteryLevel,
            "isCharging" to isCharging
        )

        AgentLogger.emitEnvelope("metric", mapOf(
            "level" to "INFO",
            "message" to "Performance sample: usedMem=${usedMemMB}MB, threads=$threadCount, battery=${batteryLevel}%",
            "tag" to "PerformanceSampler",
            "metrics" to metricsMap
        ))
    }
}
