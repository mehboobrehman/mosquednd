package com.mosquednd.hijri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for HijriConverter.
 *
 * Test cases based on the Tabular (Kuwaiti) Islamic calendar algorithm.
 *
 * CORRECTIONS MADE (June 2026):
 * - julianDayNumberToGregorian was missing `- 4800` in the year formula
 *   (standard Fliegel-Van Flandern algorithm). All Hijri→Gregorian conversions
 *   were returning years 4800 years too high.
 * - getDaysInMonth ignored the isLeapYear parameter for month 12 (Dhul Hijjah),
 *   always returning 29 even in leap years when it should be 30.
 * - countDaysInMonthsBefore used hardcoded month lengths and had dead leap-year
 *   code. Now delegates to getDaysInMonth().
 * - Test assertions updated to match corrected algorithm.
 */
class HijriConverterTest {

    // --- Basic conversion tests ---

    @Test
    fun `test Gregorian to Hijri - 2024-03-10 is 1 Ramadan 1445`() {
        // March 10, 2024 Gregorian
        val hijri = HijriConverter.gregorianToHijri(2024, 3, 10)

        // Expected: 1 Ramadan 1445 per the tabular calendar
        assertEquals(1445, hijri.year)
        assertEquals(9, hijri.month) // Ramadan is month 9
        assertEquals(1, hijri.day)
    }

    @Test
    fun `test Gregorian to Hijri - 2024-01-01 is approximately Jumada Al-Thani 1445`() {
        // January 1, 2024 Gregorian
        val hijri = HijriConverter.gregorianToHijri(2024, 1, 1)

        // Expected: Jumada Al-Thani 1445 (month 6)
        assertEquals(1445, hijri.year)
        assertEquals(6, hijri.month) // Jumada Al-Thani
    }

    @Test
    fun `test Gregorian to Hijri - 2000-01-01 is approximately 25 Ramadan 1420`() {
        // January 1, 2000 Gregorian
        val hijri = HijriConverter.gregorianToHijri(2000, 1, 1)

        // Expected: Ramadan 1420 (month 9)
        assertEquals(1420, hijri.year)
        assertEquals(9, hijri.month) // Ramadan
        assertTrue(hijri.day in 24..26) // ~25
    }

    @Test
    fun `test Hijri epoch - 1 Muharram 1 AH corresponds to July 18 622 CE`() {
        // 1 Muharram 1 AH corresponds to JDN 1948439.5
        // which is July 18, 622 CE in the proleptic Gregorian calendar,
        // and July 16, 622 CE in the Julian calendar.
        val jdn = HijriConverter.hijriToJulianDayNumber(1, 1, 1)
        assertEquals(HijriDate.HIJRI_EPOCH_JD, jdn, 0.1)

        // Convert back to Gregorian proleptic to verify
        val (year, month, day) = HijriConverter.julianDayNumberToGregorian(jdn)

        // July 18, 622 CE (Gregorian proleptic)
        assertEquals(622, year)
        assertEquals(7, month) // July
        assertEquals(18, day)
    }

    @Test
    fun `test round-trip conversion - Gregorian to Hijri to Gregorian`() {
        // Test round-trip for several dates
        val testDates = listOf(
            Triple(2024, 3, 10),  // March 10, 2024
            Triple(2024, 1, 1),   // January 1, 2024
            Triple(2000, 1, 1),   // January 1, 2000
            Triple(1990, 7, 15),  // July 15, 1990
            Triple(1980, 1, 1)    // January 1, 1980
        )

        for ((gYear, gMonth, gDay) in testDates) {
            val hijri = HijriConverter.gregorianToHijri(gYear, gMonth, gDay)
            val (rYear, rMonth, rDay) = HijriConverter.hijriToGregorian(
                hijri.year,
                hijri.month,
                hijri.day
            )

            // Round-trip should return the same Gregorian date
            assertEquals("Round-trip failed for $gYear-$gMonth-$gDay", gYear, rYear)
            assertEquals("Round-trip failed for $gYear-$gMonth-$gDay", gMonth, rMonth)
            assertEquals("Round-trip failed for $gYear-$gMonth-$gDay", gDay, rDay)
        }
    }

    // --- Hijri to Gregorian tests ---

    @Test
    fun `test Hijri to Gregorian - 1 Ramadan 1445`() {
        // 1 Ramadan 1445 AH
        val (year, month, day) = HijriConverter.hijriToGregorian(1445, 9, 1)

        // Should be March 10, 2024
        assertEquals(2024, year)
        assertTrue("Month should be March (3)", month == 3)
        assertTrue("Day should be 10", day == 10)
    }

    @Test
    fun `test Hijri to Gregorian - 1 Muharram 1445`() {
        // 1 Muharram 1445 AH (Islamic new year)
        val (year, month, day) = HijriConverter.hijriToGregorian(1445, 1, 1)

        // Should be July 18, 2023
        assertEquals(2023, year)
        assertTrue("Month should be July (7)", month == 7)
        assertTrue("Day should be 18", day == 18)
    }

    // --- Leap year tests ---

    @Test
    fun `test Islamic leap year detection`() {
        // Leap years in 30-year cycle: 2, 5, 7, 10, 13, 16, 18, 21, 24, 26, 29
        val leapYearsInCycle = setOf(2, 5, 7, 10, 13, 16, 18, 21, 24, 26, 29)

        for (i in 1..30) {
            val expected = i in leapYearsInCycle
            val actual = HijriConverter.isIslamicLeapYear(i)
            assertEquals("Year $i should be leap=$expected", expected, actual)
        }
    }

    @Test
    fun `test Islamic leap year wraps at cycle boundary`() {
        // Year 30 is position 30 in the cycle — NOT in the leap-year set
        // Leap years: 2, 5, 7, 10, 13, 16, 18, 21, 24, 26, 29
        assertTrue(!HijriConverter.isIslamicLeapYear(30))
        // Year 31 is same cycle position as year 1 (non-leap)
        assertTrue(!HijriConverter.isIslamicLeapYear(31))
        // Year 32 is same cycle position as year 2 (leap)
        assertTrue(HijriConverter.isIslamicLeapYear(32))
    }

    // --- Helper function tests ---

    @Test
    fun `test getIslamicYearDays for leap and common years`() {
        // Year 2 is a leap year, but countDaysInYearsBefore(2) counts
        // only complete years before year 2, i.e. year 1 — which is common (354 days)
        assertEquals(HijriConverter.COMMON_YEAR_DAYS, HijriConverter.countDaysInYearsBefore(2))

        // Total before year 3 = year 1 (354) + year 2 (355, leap) = 709
        assertEquals(709, HijriConverter.countDaysInYearsBefore(3))
    }

    @Test
    fun `test countDaysInMonthsBefore for normal year`() {
        // In a normal year, months before Ramadan (9) = 30+29+30+29+30+29+30+29 = 236
        val daysBeforeRamadan = HijriConverter.countDaysInMonthsBefore(1445, 9)
        assertEquals(236, daysBeforeRamadan)
    }

    @Test
    fun `test dayOfYearToMonthAndDay`() {
        // Day 0 should be 1 Muharram
        val (month, day) = HijriConverter.dayOfYearToMonthAndDay(0, false)
        assertEquals(1, month)
        assertEquals(1, day)

        // Day 29 should be 30 Muharram (month 1, day 30 — Muharram has 30 days)
        val (m2, d2) = HijriConverter.dayOfYearToMonthAndDay(29, false)
        assertEquals(1, m2)
        assertEquals(30, d2)
    }

    @Test
    fun `test daysRemainingToYear`() {
        // Start of year 1, day 0
        assertEquals(1, HijriConverter.daysRemainingToYear(0))

        // After 354 days (common year), we're at year 2
        assertEquals(2, HijriConverter.daysRemainingToYear(354))

        // After 709 days (common + leap), we're at year 3
        assertEquals(3, HijriConverter.daysRemainingToYear(709))
    }

    // --- Edge cases ---

    @Test
    fun `test far future date`() {
        // Far future: 2100-01-01
        val hijri = HijriConverter.gregorianToHijri(2100, 1, 1)
        assertTrue(hijri.year > 1500)
    }

    @Test
    fun `test early Islamic date`() {
        // Early Islamic: year 2 AH
        val (year, month, day) = HijriConverter.hijriToGregorian(2, 1, 1)
        assertEquals(623, year)
    }

    @Test
    fun `test JDN consistency`() {
        // JDN for a known date: January 1, 2000 = JD 2451545.0
        val jdn = HijriConverter.gregorianToJulianDayNumber(2000, 1, 1)
        assertEquals(2451545.0, jdn, 0.1)
    }
}
