package com.mosquednd.util

import org.junit.Assert.*
import org.junit.Test

class RetryStrategyTest {

    @Test
    fun `test retry strategy initial delay and exponential backoff`() {
        val strategy = RetryStrategy(maxRetries = 3, initialDelayMs = 1000L)
        
        assertTrue(strategy.canRetry())
        assertEquals(1000L, strategy.nextDelay())
        
        assertTrue(strategy.canRetry())
        assertEquals(2000L, strategy.nextDelay())
        
        assertTrue(strategy.canRetry())
        assertEquals(4000L, strategy.nextDelay())
        
        assertFalse(strategy.canRetry())
    }

    @Test
    fun `test retry strategy reset`() {
        val strategy = RetryStrategy(maxRetries = 2, initialDelayMs = 500L)
        
        assertEquals(500L, strategy.nextDelay())
        assertEquals(1000L, strategy.nextDelay())
        assertFalse(strategy.canRetry())
        
        strategy.reset()
        assertTrue(strategy.canRetry())
        assertEquals(500L, strategy.nextDelay())
    }
}
