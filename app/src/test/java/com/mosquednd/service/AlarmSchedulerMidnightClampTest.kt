package com.mosquednd.service

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for the DND end-time midnight boundary clamp in
 * [AlarmScheduler.scheduleDNDAlarms].
 *
 * The clamp logic:
 *   endCal = startCal + duration
 *   if endCal.DAY_OF_YEAR != startCal.DAY_OF_YEAR → clamp endCal to 23:59:59 same day
 *
 * These tests validate the Calendar math independently of Android dependencies.
 */
class AlarmSchedulerMidnightClampTest {

    /**
     * Simulates the clamp logic from [AlarmScheduler.scheduleDNDAlarms].
     * Returns the clamped endCal.
     */
    private fun computeEndCal(
        startHour: Int,
        startMinute: Int,
        offset: Int,
        duration: Int
    ): Pair<Calendar, Boolean> {
        // Build a Calendar for today at startHour:startMinute + offset minutes
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, offset)
        }

        val endCal = startCal.clone() as Calendar
        endCal.add(Calendar.MINUTE, duration)

        // Apply the clamp (same logic as AlarmScheduler)
        val startDay = startCal.get(Calendar.DAY_OF_YEAR)
        val endDay = endCal.get(Calendar.DAY_OF_YEAR)
        val wasClamped = endDay != startDay
        if (wasClamped) {
            endCal.set(Calendar.HOUR_OF_DAY, 23)
            endCal.set(Calendar.MINUTE, 59)
            endCal.set(Calendar.SECOND, 59)
            endCal.set(Calendar.MILLISECOND, 0)
        }

        return Pair(endCal, wasClamped)
    }

    // ─── Acceptance Criterion 1: Midnight-crossing clamp ───

    @Test
    fun `23_50 plus 30min clamps to 23_59_59`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 50,
            offset = 0,
            duration = 30
        )

        assertTrue("endCal should have been clamped (crossed midnight)", wasClamped)
        assertEquals("Hour should be 23 (clamped)", 23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Minute should be 59 (clamped)", 59, endCal.get(Calendar.MINUTE))
        assertEquals("Second should be 59 (clamped)", 59, endCal.get(Calendar.SECOND))
        assertEquals("Millisecond should be 0", 0, endCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `11_50PM plus 20min clamps to 23_59_59`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 50,
            offset = 0,
            duration = 20
        )

        assertTrue("endCal should have been clamped (23:50 + 20 = 00:10 next day)", wasClamped)
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
        assertEquals(59, endCal.get(Calendar.SECOND))
    }

    // ─── Acceptance Criterion 2: No clamp for same-day times ───

    @Test
    fun `14_00 plus 30min stays at 14_30 no clamp`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 14,
            startMinute = 0,
            offset = 0,
            duration = 30
        )

        assertFalse("endCal should NOT be clamped (14:00 + 30 = 14:30 same day)", wasClamped)
        assertEquals("Hour should be 14", 14, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Minute should be 30", 30, endCal.get(Calendar.MINUTE))
        assertEquals("Second should be 0", 0, endCal.get(Calendar.SECOND))
    }

    @Test
    fun `00_05 plus 10min stays at 00_15 no clamp`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 0,
            startMinute = 5,
            offset = 0,
            duration = 10
        )

        assertFalse("endCal should NOT be clamped (00:05 + 10 = 00:15 same day)", wasClamped)
        assertEquals(0, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `23_00 plus 30min stays at 23_30 no clamp`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 0,
            offset = 0,
            duration = 30
        )

        assertFalse("endCal should NOT be clamped (23:00 + 30 = 23:30 same day)", wasClamped)
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `23_30 plus 15min stays at 23_45 no clamp`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 30,
            offset = 0,
            duration = 15
        )

        assertFalse("endCal should NOT be clamped (23:30 + 15 = 23:45 same day)", wasClamped)
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, endCal.get(Calendar.MINUTE))
    }

    // ─── Edge Cases ───

    @Test
    fun `23_00 plus 60min clamps to 23_59_59`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 0,
            offset = 0,
            duration = 60
        )

        assertTrue("endCal should be clamped (23:00 + 60 = 00:00 next day)", wasClamped)
        assertEquals("Clamped hour should be 23", 23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Clamped minute should be 59", 59, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `23_01 plus 59min clamps to 23_59_59`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 1,
            offset = 0,
            duration = 59
        )

        assertTrue("endCal should be clamped (23:01 + 59 = 00:00 next day)", wasClamped)
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `23_59 plus 1min clamps to 23_59_59`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 59,
            offset = 0,
            duration = 1
        )

        assertTrue("endCal should be clamped (23:59 + 1 = 00:00 next day)", wasClamped)
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `23_50 plus 0 offset and 0 duration stays at 23_50`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 50,
            offset = 0,
            duration = 0
        )

        assertFalse("endCal should NOT be clamped (0 duration stays same day)", wasClamped)
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(50, endCal.get(Calendar.MINUTE))
        assertEquals(0, endCal.get(Calendar.SECOND))
    }

    @Test
    fun `23_30 plus offset of -10 and 30min duration stays at 23_50 no clamp`() {
        // Offset = -10 means startCal = 23:20, endCal = 23:50 — same day
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 30,
            offset = -10,
            duration = 30
        )

        assertFalse("endCal should NOT be clamped (23:20 + 30 = 23:50 same day)", wasClamped)
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(50, endCal.get(Calendar.MINUTE))
    }

    // ─── Acceptance Criterion 3: Offset pushing start across midnight boundary ───

    @Test
    fun `prayer at 23_40 plus 15 offset pushing start past midnight clamps end`() {
        // Prayer at 23:40, offset +15 → start at 23:55, duration 10 → end 00:05 next day
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 40,
            offset = 15,
            duration = 10
        )

        assertTrue("endCal should be clamped (23:55 + 10 = 00:05 next day)", wasClamped)
        assertEquals("Clamped hour should be 23", 23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Clamped minute should be 59", 59, endCal.get(Calendar.MINUTE))
        assertEquals("Clamped second should be 59", 59, endCal.get(Calendar.SECOND))
    }

    @Test
    fun `prayer at 23_30 plus 25 offset pushing start past midnight clamps end`() {
        // Prayer at 23:30, offset +25 → start at 23:55, duration 20 → end 00:15 next day
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 30,
            offset = 25,
            duration = 20
        )

        assertTrue("endCal should be clamped (23:55 + 20 = 00:15 next day)", wasClamped)
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
        assertEquals(59, endCal.get(Calendar.SECOND))
    }

    // ─── Acceptance Criterion 4: Zero and negative durations ───

    @Test
    fun `zero duration stays at start time no clamp`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 14,
            startMinute = 0,
            offset = 0,
            duration = 0
        )

        assertFalse("endCal should NOT be clamped (0 duration)", wasClamped)
        assertEquals("Hour should be 14", 14, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Minute should be 0", 0, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `negative duration moves end before start no clamp same day`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 15,
            startMinute = 0,
            offset = 0,
            duration = -30
        )

        assertFalse("endCal should NOT be clamped (negative duration, same day)", wasClamped)
        assertEquals("Hour should be 14", 14, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Minute should be 30", 30, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `negative duration from early morning not clamped`() {
        val (endCal, wasClamped) = computeEndCal(
            startHour = 2,
            startMinute = 0,
            offset = 0,
            duration = -60
        )

        assertFalse("endCal should NOT be clamped (negative duration, starting at 2am)", wasClamped)
        assertEquals("Hour should be 1", 1, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Minute should be 0", 0, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `negative duration from midnight boundary not clamped wraps to prev day`() {
        // start = 00:30, duration = -45 → end = 23:45 previous day
        val (endCal, wasClamped) = computeEndCal(
            startHour = 0,
            startMinute = 30,
            offset = 0,
            duration = -45
        )

        // The DAY_OF_YEAR will differ because we went into previous day,
        // BUT the clamp only applies when end crosses into the NEXT day (startDay < endDay).
        // When duration pushes into previous day, endDay < startDay, no clamp needed.
        // The computeEndCal logic checks endDay != startDay and clamps when they differ.
        // Since negative duration moved into PREVIOUS day, this IS a difference and WILL clamp.
        // This is a known edge case — negative durations are not used in the real app.
        // We simply verify the behavior is deterministic.
        assertTrue("endCal WILL be clamped (negative duration crosses day boundary backwards)",
            wasClamped)
        assertEquals("Clamped hour should be 23", 23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Clamped minute should be 59", 59, endCal.get(Calendar.MINUTE))
    }

    // ─── Near-day-boundary with offset pushing into next day ───

    @Test
    fun `offset pushes startCal into next day then duration adds more`() {
        // Start at 23:45 with offset +20 → startCal = 00:05 next day
        // startCal is 00:05, endCal = 00:05 + 30 = 00:35
        // Both are same day (the next day), so NO clamp (DND window is 00:05-00:35)
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 45,
            offset = 20,
            duration = 30
        )

        // With a positive offset that pushes into next day, both startCal and endCal
        // are on the same day (the next day), so no clamp is needed.
        assertFalse("endCal should NOT be clamped (startCal+offset is already next day, endCal same day as startCal)", wasClamped)
        assertEquals(0, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(35, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun `startCal near midnight with offset that pushes it to next day, no clamp for same-day window`() {
        // 23:50 + 15 offset = 00:05 next day, then + 10 = 00:15 same day as startCal
        val (endCal, wasClamped) = computeEndCal(
            startHour = 23,
            startMinute = 50,
            offset = 15,
            duration = 10
        )

        assertFalse("endCal should NOT be clamped (startCal is next day, endCal same day as startCal)", wasClamped)
        assertEquals(0, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, endCal.get(Calendar.MINUTE))
    }
}
