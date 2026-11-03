package com.mosquednd.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicLong

/**
 * AnrWatchdog: Monitors main thread responsiveness by periodically posting ticks to the main looper.
 * Emits ANR events if the main thread is unresponsive for longer than the threshold.
 */
class AnrWatchdog(
    private val anrThresholdMs: Long = 3000L,
    private val checkIntervalMs: Long = 1000L,
    private val onAnrDetected: (String) -> Unit
) : Thread("AgentLogger-AnrWatchdog") {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastTickTime = AtomicLong(System.currentTimeMillis())
    @Volatile private var running = true
    private var lastAnrReportTime = 0L

    init {
        isDaemon = true
    }

    override fun run() {
        while (running) {
            val now = System.currentTimeMillis()
            lastTickTime.set(now)
            mainHandler.post {
                lastTickTime.set(System.currentTimeMillis())
            }

            try {
                Thread.sleep(checkIntervalMs)
            } catch (_: InterruptedException) {
                break
            }

            if (!running) break

            val elapsed = System.currentTimeMillis() - lastTickTime.get()
            if (elapsed > anrThresholdMs) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAnrReportTime > 15_000L) { // 15s cooldown between ANR reports
                    lastAnrReportTime = currentTime
                    try {
                        val mainThread = Looper.getMainLooper().thread
                        val stack = mainThread.stackTrace
                        val stackStr = stack.joinToString("\n") { "  at $it" }
                        onAnrDetected(stackStr)
                    } catch (e: Exception) {
                        onAnrDetected("ANR detected but failed to capture stack trace: ${e.message}")
                    }
                }
            }
        }
    }

    fun stopWatchdog() {
        running = false
        interrupt()
    }
}
