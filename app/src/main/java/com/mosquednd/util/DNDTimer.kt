package com.mosquednd.util

import android.util.Log
import java.util.Timer
import java.util.TimerTask

/**
 * A timer that counts down from a configurable timeout and calls [onTimeout] when elapsed.
 * Uses a stored start timestamp for correct elapsed-time calculation across app backgrounding.
 *
 * On Android this uses [java.util.Timer] for scheduling. The timestamp-based calculation
 * works correctly after app backgrounding.
 *
 * @param onTimeout callback invoked when the timer expires
 * @param timeoutMs duration in milliseconds; defaults to 15 minutes
 */
class DNDTimer(
    private val onTimeout: () -> Unit,
    private val timeoutMs: Long = TIMEOUT_MS
) {
    companion object {
        /** Default timeout: 15 minutes in milliseconds */
        const val TIMEOUT_MS = 15 * 60 * 1000L
        private const val TAG = "DNDTimer"
    }

    @Volatile
    var running = false
        private set

    private var startTimestamp: Long = 0L
    private var timer: Timer? = null

    /** Runnable that fires on timeout — internal so tests can invoke it directly. */
    internal val timeoutRunnable = Runnable {
        running = false
        startTimestamp = 0L
        onTimeout()
    }

    /**
     * Start the timer. No-op if already running; call [reset] to restart.
     */
    fun start() {
        if (running) {
            logDebug("start() ignored — timer already running")
            return
        }
        running = true
        startTimestamp = System.currentTimeMillis()
        timer = Timer("DNDTimer", false).apply {
            schedule(object : TimerTask() {
                override fun run() {
                    timeoutRunnable.run()
                }
            }, timeoutMs)
        }
        logDebug("Timer started ($timeoutMs ms)")
    }

    /**
     * Cancel the timer. Safe to call when not running (no-op).
     */
    fun cancel() {
        if (!running) {
            logDebug("cancel() called but timer not running")
            return
        }
        timer?.cancel()
        timer = null
        running = false
        startTimestamp = 0L
        logDebug("Timer cancelled")
    }

    /**
     * Cancel any existing timer and start fresh. Safe to call when not running.
     */
    fun reset() {
        timer?.cancel()
        timer = null
        running = true
        startTimestamp = System.currentTimeMillis()
        timer = Timer("DNDTimer", false).apply {
            schedule(object : TimerTask() {
                override fun run() {
                    timeoutRunnable.run()
                }
            }, timeoutMs)
        }
        logDebug("Timer reset ($timeoutMs ms)")
    }

    fun isRunning(): Boolean = running

    /**
     * Returns remaining milliseconds based on elapsed time since start.
     * Uses timestamp-based calculation so it works correctly after app backgrounding.
     * Returns 0 if timer is not running or has already elapsed.
     */
    fun getRemainingMs(): Long {
        if (!running || startTimestamp == 0L) return 0L
        val elapsed = System.currentTimeMillis() - startTimestamp
        val remaining = timeoutMs - elapsed
        return if (remaining > 0) remaining else 0L
    }

    /**
     * Safe logging that works in both Android runtime and JVM test environments.
     * On JVM, `android.util.Log` stubs throw RuntimeException, so we catch silently.
     */
    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // Silently ignore — Log stubs on JVM throw RuntimeException("Stub!")
        }
    }
}
