package com.mosquednd.util

class RetryStrategy(private val maxRetries: Int = 5, private val initialDelayMs: Long = 1000L) {
    private var retries = 0
    private var delay = initialDelayMs

    fun nextDelay(): Long {
        val d = delay
        delay = (delay * 2).coerceAtMost(30000L)
        retries++
        return d
    }
    fun reset() {
        retries = 0
        delay = initialDelayMs
    }
    fun canRetry(): Boolean = retries < maxRetries
}
