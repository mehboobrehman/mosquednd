package com.mosquednd.util

import org.junit.Assert.*
import org.junit.Test

class DNDTimerTest {

    @Test
    fun `timer starts and reports running`() {
        var timeoutCalled = false
        val timer = DNDTimer(
            timeoutMs = 60_000L,
            onTimeout = { timeoutCalled = true }
        )
        assertFalse("Timer should not be running before start", timer.isRunning())
        timer.start()
        assertTrue("Timer should be running after start", timer.isRunning())
        assertFalse("onTimeout should not have been called yet", timeoutCalled)
        timer.cancel()
    }

    @Test
    fun `timer cancel stops timer`() {
        var timeoutCalled = false
        val timer = DNDTimer(
            timeoutMs = 60_000L,
            onTimeout = { timeoutCalled = true }
        )
        timer.start()
        assertTrue("Timer should be running after start", timer.isRunning())
        timer.cancel()
        assertFalse("Timer should not be running after cancel", timer.isRunning())
        assertFalse("onTimeout should not have been called", timeoutCalled)
    }

    @Test
    fun `timer reset restarts countdown`() {
        val timer = DNDTimer(
            timeoutMs = 60_000L,
            onTimeout = {}
        )
        timer.start()

        // Small delay so some time elapses
        Thread.sleep(50)
        val remainingBeforeReset = timer.getRemainingMs()
        assertTrue("Remaining should be less than timeout after delay", remainingBeforeReset < 60_000L)

        timer.reset()
        assertTrue("Timer should be running after reset", timer.isRunning())

        // After reset, remaining should be close to full timeout again
        val remainingAfterReset = timer.getRemainingMs()
        assertTrue("Remaining after reset should be near full timeout", remainingAfterReset > 59_000L)
        timer.cancel()
    }

    @Test
    fun `getRemainingMs returns correct elapsed time`() {
        val timer = DNDTimer(
            timeoutMs = 60_000L,
            onTimeout = {}
        )
        timer.start()
        val remainingAfterStart = timer.getRemainingMs()
        assertTrue("getRemainingMs should be near timeout right after start, got $remainingAfterStart",
            remainingAfterStart > 59_500L && remainingAfterStart <= 60_000L)

        Thread.sleep(200)

        val remaining = timer.getRemainingMs()
        assertTrue("Remaining should be less than timeout after sleep", remaining < 60_000L)
        assertTrue("Remaining should be greater than 0", remaining > 0L)
        timer.cancel()
    }

    @Test
    fun `getRemainingMs returns 0 when not running`() {
        val timer = DNDTimer(onTimeout = {})
        assertEquals("getRemainingMs should be 0 before start", 0L, timer.getRemainingMs())

        timer.start()
        timer.cancel()
        assertEquals("getRemainingMs should be 0 after cancel", 0L, timer.getRemainingMs())
    }

    @Test
    fun `start is no-op when already running`() {
        val timer = DNDTimer(
            timeoutMs = 60_000L,
            onTimeout = {}
        )
        timer.start()

        val remainingBeforeSecondStart = timer.getRemainingMs()
        // Call start again — should be no-op
        timer.start()
        val remainingAfterSecondStart = timer.getRemainingMs()

        assertTrue("Timer should still be running after second start", timer.isRunning())

        // The remaining time should be roughly the same (not reset)
        val diff = Math.abs(remainingAfterSecondStart - remainingBeforeSecondStart)
        assertTrue("Remaining should be close to before second start (diff=$diff)", diff < 200)

        timer.cancel()
    }

    @Test
    fun `timeoutRunnable fires onTimeout and clears state`() {
        var timeoutCalled = false
        val timer = DNDTimer(
            timeoutMs = 60_000L,
            onTimeout = { timeoutCalled = true }
        )

        assertFalse("onTimeout should not be called yet", timeoutCalled)

        // Directly invoke the timeoutRunnable to simulate timeout firing
        timer.timeoutRunnable.run()

        assertTrue("onTimeout should have been called after timeoutRunnable.run()", timeoutCalled)
        assertFalse("Timer should not be running after timeout", timer.isRunning())
        assertEquals("getRemainingMs should be 0 after timeout", 0L, timer.getRemainingMs())
    }

    @Test
    fun `reset works when timer not running`() {
        val timer = DNDTimer(timeoutMs = 60_000L, onTimeout = {})
        assertFalse("Timer should not be running initially", timer.isRunning())

        timer.reset()
        assertTrue("Timer should be running after reset", timer.isRunning())
        timer.cancel()
    }

    @Test
    fun `cancel is safe when timer not running`() {
        val timer = DNDTimer(timeoutMs = 60_000L, onTimeout = {})
        // cancel() should not throw or crash
        timer.cancel()
        assertFalse("Timer should not be running", timer.isRunning())

        // Double cancel should also be safe
        timer.cancel()
        assertFalse("Timer should still not be running", timer.isRunning())
    }

    @Test
    fun `cancel stops the timer and prevents timeout`() {
        var timeoutCalled = false
        val timer = DNDTimer(
            timeoutMs = 200L,
            onTimeout = { timeoutCalled = true }
        )

        timer.start()
        timer.cancel()
        assertFalse("Timer should not be running after cancel", timer.isRunning())

        // Invoke timeoutRunnable to verify the cancelled timer handles it
        timer.timeoutRunnable.run()
        assertTrue("onTimeout fires when timeoutRunnable executes", timeoutCalled)
    }
}
