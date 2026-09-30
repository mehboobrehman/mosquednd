package com.mosquednd.calendar

import com.mosquednd.prayer.InMemorySharedPreferences
import com.mosquednd.prayer.PrayerAlarmPrefs
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for IslamicCalendar using an in-memory SharedPreferences implementation.
 */
class IslamicCalendarTest {

    private lateinit var testPrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        testPrefs = InMemorySharedPreferences()
        PrayerAlarmPrefs.initForTest(testPrefs)
    }

    @Test
    fun `hijri date offset high raw prefs clamped to +2 and valid date returned`() {
        testPrefs.edit().putInt("hijri_date_offset", 5).apply()
        val hijriDate = IslamicCalendar.getCurrentHijriDate()
        assertTrue("Hijri year should be valid after clamping: ${hijriDate.year}", hijriDate.year > 1300)
        assertTrue("Hijri month should be valid after clamping: ${hijriDate.month}", hijriDate.month in 1..12)
        assertTrue("Hijri day should be valid after clamping: ${hijriDate.day}", hijriDate.day in 1..30)
    }

    @Test
    fun `hijri date offset low raw prefs clamped to -2 and valid date returned`() {
        testPrefs.edit().putInt("hijri_date_offset", -5).apply()
        val hijriDate = IslamicCalendar.getCurrentHijriDate()
        assertTrue("Hijri year should be valid after clamping: ${hijriDate.year}", hijriDate.year > 1300)
        assertTrue("Hijri month should be valid after clamping: ${hijriDate.month}", hijriDate.month in 1..12)
        assertTrue("Hijri day should be valid after clamping: ${hijriDate.day}", hijriDate.day in 1..30)
    }
}
