package com.mosquednd.hijri

/**
 * Hijri/Gregorian date converter using the Tabular Islamic calendar algorithm.
 *
 * Based on the Kuwaiti algorithm for the Tabular Islamic calendar.
 * This provides a consistent conversion between Gregorian and Hijri calendars.
 */
object HijriConverter {

    /**
     * Hijri epoch (July 16, 622 CE in Julian Day Number).
     */
    const val HIJRI_EPOCH_JD = 1948439.5

    /**
     * Common year length in days (354).
     */
    const val COMMON_YEAR_DAYS = 354

    /**
     * Leap year length in days (355).
     */
    const val LEAP_YEAR_DAYS = 355

    // 30-year cycle lengths (sum of 11 leap years = 11*355 + 19*354 = 10631)
    private const val CYCLE_DAYS = 10631

    /**
     * Converts a Gregorian date to Hijri date.
     *
     * @param year  Gregorian year
     * @param month Gregorian month (1-12)
     * @param day   Gregorian day (1-31)
     * @return HijriDate containing year, month, and day
     */
    fun gregorianToHijri(year: Int, month: Int, day: Int): HijriDate {
        val jdn = gregorianToJulianDayNumber(year, month, day)
        return julianDayNumberToHijri(jdn)
    }

    /**
     * Converts a Hijri date to Gregorian date.
     *
     * @param year  Hijri year
     * @param month Hijri month (1-12)
     * @param day   Hijri day (1-30)
     * @return Triple of (year, month, day) in Gregorian calendar
     */
    fun hijriToGregorian(year: Int, month: Int, day: Int): Triple<Int, Int, Int> {
        val jdn = hijriToJulianDayNumber(year, month, day)
        return julianDayNumberToGregorian(jdn)
    }

    /**
     * Converts Gregorian date to Julian Day Number.
     *
     * @param year  Gregorian year
     * @param month Gregorian month (1-12)
     * @param day   Gregorian day (1-31)
     * @return Julian Day Number as Double
     */
    fun gregorianToJulianDayNumber(year: Int, month: Int, day: Int): Double {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3

        val jdn = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045.0
        return jdn
    }

    /**
     * Converts Julian Day Number to Gregorian date.
     *
     * @param jdn Julian Day Number
     * @return Triple of (year, month, day) in Gregorian calendar
     */
    fun julianDayNumberToGregorian(jdn: Double): Triple<Int, Int, Int> {
        val a = (jdn + 32044.0).toLong()
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4

        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153

        val dayInt = (e - (153 * m + 2) / 5 + 1).toInt()
        val monthInt = (m + 3 - 12 * (m / 10)).toInt()
        val yearInt = (100 * b + d + m / 10 - 4800).toInt()

        return Triple(yearInt, monthInt, dayInt)
    }

    /**
     * Converts Hijri date to Julian Day Number.
     *
     * @param year  Hijri year
     * @param month Hijri month (1-12)
     * @param day   Hijri day (1-30)
     * @return Julian Day Number as Double
     */
    fun hijriToJulianDayNumber(year: Int, month: Int, day: Int): Double {
        return (day
                + countDaysInMonthsBefore(year, month)
                + countDaysInYearsBefore(year)
                + HIJRI_EPOCH_JD - 1)
    }

    /**
     * Converts Julian Day Number to Hijri date.
     *
     * @param jdn Julian Day Number
     * @return HijriDate containing year, month, and day
     */
    fun julianDayNumberToHijri(jdn: Double): HijriDate {
        // Use integer JDN (floor) for day-level precision
        val jd = jdn.toLong()
        // Integer epoch: floor of HIJRI_EPOCH_JD (1948439.5) = 1948439
        val epoch = HIJRI_EPOCH_JD.toLong() // 1948439

        // Days since Hijri epoch
        val daysSinceEpoch = jd - epoch

        // Determine year using 30-year cycle (10631 days per cycle)
        val completeCycles = daysSinceEpoch / 10631
        var remainingDays = (daysSinceEpoch % 10631).toInt()

        var yearInCycle = 0
        for (i in 0 until 30) {
            val yearDays = if (isIslamicLeapYear(i + 1)) LEAP_YEAR_DAYS else COMMON_YEAR_DAYS
            if (remainingDays < yearDays) {
                yearInCycle = i + 1
                break
            }
            remainingDays -= yearDays
        }

        val hijriYear = (completeCycles * 30 + yearInCycle).toInt()

        // Determine month
        val isLeap = isIslamicLeapYear(hijriYear)
        var hijriMonth = 1
        var dayOfYear = remainingDays

        for (m in 1..12) {
            val monthLength = getDaysInMonth(m, isLeap)
            if (dayOfYear < monthLength) {
                hijriMonth = m
                break
            }
            dayOfYear -= monthLength
            if (m == 12) {
                hijriMonth = 12
            }
        }

        val hijriDay = dayOfYear + 1

        return HijriDate(hijriYear, hijriMonth, hijriDay)
    }

    /**
     * Determines if a Hijri year is a leap year.
     *
     * Leap years in the 30-year cycle: 2, 5, 7, 10, 13, 16, 18, 21, 24, 26, 29
     *
     * @param year Hijri year
     * @return true if leap year, false otherwise
     */
    fun isIslamicLeapYear(year: Int): Boolean {
        val cyclePosition = ((year - 1) % 30) + 1
        return cyclePosition in listOf(2, 5, 7, 10, 13, 16, 18, 21, 24, 26, 29)
    }

    /**
     * Gets the number of days in a Hijri year.
     *
     * @param year Hijri year
     * @return Number of days (354 or 355)
     */
    fun getIslamicYearDays(year: Int): Int {
        return if (isIslamicLeapYear(year)) LEAP_YEAR_DAYS else COMMON_YEAR_DAYS
    }

    /**
     * Gets the number of days in a specific Hijri month.
     * Odd months (1,3,5,7,9,11) have 30 days; even months (2,4,6,8,10,12) have 29 days.
     */
    fun getDaysInMonth(month: Int, isLeapYear: Boolean): Int {
        // Odd months (1,3,5,7,9,11) have 30 days.
        // Even months (2,4,6,8,10,12) have 29 days, except Dhul Hijjah (12) has 30 in leap years.
        return when {
            month % 2 == 1 -> 30
            month == 12 && isLeapYear -> 30
            else -> 29
        }
    }

    /**
     * Counts total days in complete years before the given year.
     *
     * @param year Hijri year
     * @return Total days in complete years before this year
     */
    fun countDaysInYearsBefore(year: Int): Int {
        if (year <= 1) return 0

        val completeCycles = (year - 1) / 30
        val yearsInCurrentCycle = (year - 1) % 30

        val daysInCompleteCycles = completeCycles * CYCLE_DAYS

        var daysInPartial = 0
        for (i in 0 until yearsInCurrentCycle) {
            daysInPartial += if (isIslamicLeapYear(i + 1)) LEAP_YEAR_DAYS else COMMON_YEAR_DAYS
        }

        return daysInCompleteCycles + daysInPartial
    }

    /**
     * Counts total days in complete months of a Hijri year before the given month.
     *
     * @param year  Hijri year
     * @param month Hijri month (1-12)
     * @return Total days in complete months before this month
     */
    fun countDaysInMonthsBefore(year: Int, month: Int): Int {
        if (month <= 1) return 0

        val isLeap = isIslamicLeapYear(year)

        var days = 0
        for (m in 1 until month) {
            days += getDaysInMonth(m, isLeap)
        }

        return days
    }

    /**
     * Converts day of year to month and day.
     *
     * @param dayOfYear Day number (0-based from start of year)
     * @param isLeapYear Whether the year is a leap year
     * @return Pair of (month, day)
     */
    fun dayOfYearToMonthAndDay(dayOfYear: Int, isLeapYear: Boolean): Pair<Int, Int> {
        var day = dayOfYear

        for (month in 1..12) {
            val monthLength = if (month % 2 == 1) 30 else 29
            if (day < monthLength) {
                return Pair(month, day + 1)
            }
            day -= monthLength
        }

        return Pair(12, 30)
    }

    /**
     * Converts days remaining to Hijri year.
     *
     * @param daysRemaining Number of days from the start
     * @return Hijri year number (1-based)
     */
    fun daysRemainingToYear(daysRemaining: Int): Int {
        var yearPosition = 1
        var remaining = daysRemaining

        while (true) {
            val yearDays = getIslamicYearDays(yearPosition)
            if (remaining < yearDays) {
                break
            }
            remaining -= yearDays
            yearPosition++
            // Safety limit to prevent infinite loop
            if (yearPosition > 10000) break
        }

        return yearPosition
    }

    /**
     * Get the start of a Hijri year in Julian Day Number.
     *
     * @param year Hijri year
     * @return JDN of the first day of the year
     */
    fun getYearStartJDN(year: Int): Double {
        return countDaysInYearsBefore(year) + HIJRI_EPOCH_JD
    }

    /**
     * Get the start of a Hijri month in Julian Day Number.
     *
     * @param year  Hijri year
     * @param month Hijri month (1-12)
     * @return JDN of the first day of the month
     */
    fun getMonthStartJDN(year: Int, month: Int): Double {
        return countDaysInMonthsBefore(year, month) + countDaysInYearsBefore(year) + HIJRI_EPOCH_JD
    }
}