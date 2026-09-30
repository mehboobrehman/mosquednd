package com.mosquednd.ui.prayer

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PrayerTimesDayChangeDetectionTest {

    private fun detectDayChange(
        lastSeenDayOfYear: Int,
        simulatedDayOfYear: Int
    ): Pair<Boolean, Int> {
        val dayChanged = simulatedDayOfYear != lastSeenDayOfYear
        val updatedLastSeen = if (dayChanged) simulatedDayOfYear else lastSeenDayOfYear
        return Pair(dayChanged, updatedLastSeen)
    }

    @Test
    fun `fresh start with lastSeenDayOfYear -1 detects change`() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val (dayChanged, updated) = detectDayChange(-1, today)
        assertTrue("Fresh start should detect day change", dayChanged)
        assertEquals("Should update to today", today, updated)
    }

    @Test
    fun `second check after fresh start does not re-detect same day`() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val (changed1, updated1) = detectDayChange(-1, today)
        assertTrue(changed1)
        val (changed2, _) = detectDayChange(updated1, today)
        assertFalse("Second check should not re-detect", changed2)
    }

    @Test
    fun `same day resume does not detect change`() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val (dayChanged, _) = detectDayChange(today, today)
        assertFalse("Same day should not detect change", dayChanged)
    }

    @Test
    fun `repeated same-day checks never detect change`() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        var lastSeen = today
        repeat(10) {
            val (dayChanged, updated) = detectDayChange(lastSeen, today)
            assertFalse("Same day should not detect change (iteration $it)", dayChanged)
            lastSeen = updated
        }
    }

    @Test
    fun `next day resume detects change`() {
        val yesterday = getYesterdayDayOfYear()
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val (dayChanged, updated) = detectDayChange(yesterday, today)
        assertTrue("Next day should detect change", dayChanged)
        assertEquals("Should update to today", today, updated)
    }

    @Test
    fun `synthetic Dec 31 to Jan 1 detects change`() {
        val (dayChanged, updated) = detectDayChange(365, 1)
        assertTrue("Dec 31 -> Jan 1 should detect change", dayChanged)
        assertEquals(1, updated)
    }

    @Test
    fun `synthetic leap year rollover detects change`() {
        val (dayChanged, updated) = detectDayChange(366, 1)
        assertTrue("Dec 31 (leap) -> Jan 1 should detect change", dayChanged)
        assertEquals(1, updated)
    }

    @Test
    fun `state machine three-day transition`() {
        val (c1, u1) = detectDayChange(-1, 100)
        assertTrue(c1); assertEquals(100, u1)

        val (c1b, _) = detectDayChange(u1, 100)
        assertFalse(c1b)

        val (c2, u2) = detectDayChange(u1, 101)
        assertTrue(c2); assertEquals(101, u2)

        val (c2b, _) = detectDayChange(u2, 101)
        assertFalse(c2b)

        val (c3, u3) = detectDayChange(u2, 200)
        assertTrue(c3); assertEquals(200, u3)
    }

    @Test
    fun `day 366 to day 365 detects change`() {
        val (dayChanged, updated) = detectDayChange(366, 365)
        assertTrue("366 -> 365 should detect change", dayChanged)
        assertEquals(365, updated)
    }

    @Test
    fun `day 1 to day 365 detects change`() {
        val (dayChanged, updated) = detectDayChange(1, 365)
        assertTrue("1 -> 365 should detect change", dayChanged)
        assertEquals(365, updated)
    }

    private fun getYesterdayDayOfYear(): Int {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        if (today > 1) return today - 1
        cal.add(Calendar.YEAR, -1)
        return cal.getActualMaximum(Calendar.DAY_OF_YEAR)
    }
}

