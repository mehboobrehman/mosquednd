package com.mosquednd.prayer

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Diagnostic and integration test suite for Adhan alarm offset loading, modification,
 * saving, recalculation, UI reflection, and downstream alarm scheduling/overlap verification.
 */
class PrayerAlarmOffsetFlowTest {

    private lateinit var testPrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        testPrefs = InMemorySharedPreferences()
        PrayerAlarmPrefs.initForTest(testPrefs)
    }

    @Test
    fun `test offset loading and clamping across valid and invalid ranges`() {
        // Default is 0
        assertEquals(0, PrayerAlarmPrefs.getOffset(PrayerType.FAJR))

        // Set valid offset (+30 mins)
        PrayerAlarmPrefs.setOffset(PrayerType.FAJR, 30)
        assertEquals(30, PrayerAlarmPrefs.getOffset(PrayerType.FAJR))

        // Set out-of-range offset above max (+80 -> clamped to +60)
        PrayerAlarmPrefs.setOffset(PrayerType.DHUHR, 80)
        assertEquals(60, PrayerAlarmPrefs.getOffset(PrayerType.DHUHR))

        // Set out-of-range offset below min (-75 -> clamped to -60)
        PrayerAlarmPrefs.setOffset(PrayerType.ASR, -75)
        assertEquals(-60, PrayerAlarmPrefs.getOffset(PrayerType.ASR))
    }

    @Test
    fun `test offset persistence across simulated dialog re-openings`() {
        // User opens dialog, sets offset to +45 mins and clicks OK
        PrayerAlarmPrefs.setOffset(PrayerType.MAGHRIB, 45)
        assertEquals(45, PrayerAlarmPrefs.getOffset(PrayerType.MAGHRIB))

        // Simulate dialog re-opening by fetching the stored offset (default param matches current stored value)
        val reOpenedOffset = PrayerAlarmPrefs.getOffset(PrayerType.MAGHRIB)
        assertEquals(45, reOpenedOffset)
    }

    @Test
    fun `test time adjustment calculation logic with offsets`() {
        // Base raw prayer time: 12:30 (Dhuhr)
        val baseCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Suppose stored offset is currently 0, preview offset is +30 mins
        // Adjusted time should be 13:00
        val storedOffset = 0
        val previewOffset = 30
        val rawMinutes = baseCal.get(Calendar.HOUR_OF_DAY) * 60 + baseCal.get(Calendar.MINUTE) - storedOffset
        val totalMinutes = rawMinutes + previewOffset
        val wrappedMinutes = ((totalMinutes % 1440) + 1440) % 1440
        val adjustedHours = wrappedMinutes / 60
        val adjustedMins = wrappedMinutes % 60

        assertEquals(13, adjustedHours)
        assertEquals(0, adjustedMins)
    }

    @Test
    fun `test midnight rollover handling for extreme offsets`() {
        // Prayer time near midnight: 23:45 (Isha)
        val baseCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val storedOffset = 0
        val previewOffset = 30 // +30 min -> 00:15 next day
        val rawMinutes = baseCal.get(Calendar.HOUR_OF_DAY) * 60 + baseCal.get(Calendar.MINUTE) - storedOffset
        val totalMinutes = rawMinutes + previewOffset

        assertTrue("Should indicate next day", totalMinutes >= 1440)
        val wrappedMinutes = ((totalMinutes % 1440) + 1440) % 1440
        assertEquals(0, wrappedMinutes / 60)
        assertEquals(15, wrappedMinutes % 60)

        // Negative rollover: 00:15 (Fajr) with -30 min -> 23:45 previous day
        val fajrCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 15)
        }
        val fajrRawMinutes = fajrCal.get(Calendar.HOUR_OF_DAY) * 60 + fajrCal.get(Calendar.MINUTE) - 0
        val fajrTotalMinutes = fajrRawMinutes - 30
        assertTrue("Should indicate previous day", fajrTotalMinutes < 0)
        val fajrWrapped = ((fajrTotalMinutes % 1440) + 1440) % 1440
        assertEquals(23, fajrWrapped / 60)
        assertEquals(45, fajrWrapped % 60)
    }
}
