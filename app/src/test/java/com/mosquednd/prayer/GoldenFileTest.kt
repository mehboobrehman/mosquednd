package com.mosquednd.prayer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.abs

class GoldenFileTest {

    companion object {
        private const val TOLERANCE_MIN = 1 // ±1 minute tolerance for golden tests

        private const val KARACHI_LAT = 24.8967
        private const val KARACHI_LNG = 67.2369

        private const val MAKKAH_LAT = 21.4225
        private const val MAKKAH_LNG = 39.8262

        private const val LONDON_LAT = 51.5074
        private const val LONDON_LNG = -0.1278
    }

    private fun makeDate(year: Int, month: Int, day: Int, tz: String): Date {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(tz))
        cal.set(year, month - 1, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun parseTime(formatted: String): Int {
        if (formatted == "--:--") return -1
        val parts = formatted.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun assertGoldenTime(expected: String, actual: String, prayer: String, city: String, season: String) {
        val expectedMin = parseTime(expected)
        val actualMin = parseTime(actual)
        val diff = abs(expectedMin - actualMin)
        assertTrue(
            "[$city - $season] $prayer: expected $expected, got $actual (diff ${diff}min exceeds ±${TOLERANCE_MIN}min tolerance)",
            diff <= TOLERANCE_MIN
        )
    }

    @Test
    fun testKarachiGoldenTimetableAcrossSeasons() {
        val tz = TimeZone.getTimeZone("Asia/Karachi")

        val goldenKarachi = mapOf(
            "Spring" to mapOf(
                PrayerType.FAJR to "05:18",
                PrayerType.SUNRISE to "06:34",
                PrayerType.DHUHR to "12:38",
                PrayerType.ASR to "16:05",
                PrayerType.MAGHRIB to "18:43",
                PrayerType.ISHA to "19:59"
            ),
            "Summer" to mapOf(
                PrayerType.FAJR to "04:13",
                PrayerType.SUNRISE to "05:42",
                PrayerType.DHUHR to "12:33",
                PrayerType.ASR to "15:54",
                PrayerType.MAGHRIB to "19:23",
                PrayerType.ISHA to "20:53"
            ),
            "Autumn" to mapOf(
                PrayerType.FAJR to "05:03",
                PrayerType.SUNRISE to "06:19",
                PrayerType.DHUHR to "12:24",
                PrayerType.ASR to "15:51",
                PrayerType.MAGHRIB to "18:29",
                PrayerType.ISHA to "19:45"
            ),
            "Winter" to mapOf(
                PrayerType.FAJR to "05:50",
                PrayerType.SUNRISE to "07:11",
                PrayerType.DHUHR to "12:29",
                PrayerType.ASR to "15:27",
                PrayerType.MAGHRIB to "17:47",
                PrayerType.ISHA to "19:08"
            )
        )

        val dates = mapOf(
            "Spring" to makeDate(2024, 3, 21, "Asia/Karachi"),
            "Summer" to makeDate(2024, 6, 21, "Asia/Karachi"),
            "Autumn" to makeDate(2024, 9, 21, "Asia/Karachi"),
            "Winter" to makeDate(2024, 12, 21, "Asia/Karachi")
        )

        for ((season, date) in dates) {
            val times = PrayTimesCalculator.calculate(date, KARACHI_LAT, KARACHI_LNG, tz, CalculationMethod.KARACHI)
            val expectedMap = goldenKarachi[season]!!
            for ((type, expectedTime) in expectedMap) {
                val actualTime = times[type]!!.formattedTime
                assertGoldenTime(expectedTime, actualTime, type.name, "Karachi", season)
            }
        }
    }

    @Test
    fun testMakkahGoldenTimetableAcrossSeasons() {
        val tz = TimeZone.getTimeZone("Asia/Riyadh")

        val goldenMakkah = mapOf(
            "Spring" to mapOf(
                PrayerType.FAJR to "05:08",
                PrayerType.SUNRISE to "06:24",
                PrayerType.DHUHR to "12:28",
                PrayerType.ASR to "15:53",
                PrayerType.MAGHRIB to "18:32",
                PrayerType.ISHA to "20:02"
            ),
            "Summer" to mapOf(
                PrayerType.FAJR to "04:11",
                PrayerType.SUNRISE to "05:39",
                PrayerType.DHUHR to "12:23",
                PrayerType.ASR to "15:42",
                PrayerType.MAGHRIB to "19:06",
                PrayerType.ISHA to "20:36"
            ),
            "Autumn" to mapOf(
                PrayerType.FAJR to "04:53",
                PrayerType.SUNRISE to "06:09",
                PrayerType.DHUHR to "12:14",
                PrayerType.ASR to "15:39",
                PrayerType.MAGHRIB to "18:18",
                PrayerType.ISHA to "19:48"
            ),
            "Winter" to mapOf(
                PrayerType.FAJR to "05:32",
                PrayerType.SUNRISE to "06:54",
                PrayerType.DHUHR to "12:19",
                PrayerType.ASR to "15:23",
                PrayerType.MAGHRIB to "17:44",
                PrayerType.ISHA to "19:14"
            )
        )

        val dates = mapOf(
            "Spring" to makeDate(2024, 3, 21, "Asia/Riyadh"),
            "Summer" to makeDate(2024, 6, 21, "Asia/Riyadh"),
            "Autumn" to makeDate(2024, 9, 21, "Asia/Riyadh"),
            "Winter" to makeDate(2024, 12, 21, "Asia/Riyadh")
        )

        for ((season, date) in dates) {
            val times = PrayTimesCalculator.calculate(date, MAKKAH_LAT, MAKKAH_LNG, tz, CalculationMethod.UMM_AL_QURA)
            val expectedMap = goldenMakkah[season]!!
            for ((type, expectedTime) in expectedMap) {
                val actualTime = times[type]!!.formattedTime
                assertGoldenTime(expectedTime, actualTime, type.name, "Makkah", season)
            }
        }
    }

    @Test
    fun testLondonGoldenTimetableAcrossSeasons() {
        val tz = TimeZone.getTimeZone("Europe/London")

        val goldenLondon = mapOf(
            "Spring" to mapOf(
                PrayerType.FAJR to "04:07",
                PrayerType.SUNRISE to "06:01",
                PrayerType.DHUHR to "12:08",
                PrayerType.ASR to "15:26",
                PrayerType.MAGHRIB to "18:15",
                PrayerType.ISHA to "20:02"
            ),
            "Autumn" to mapOf(
                PrayerType.FAJR to "04:51",
                PrayerType.SUNRISE to "06:45",
                PrayerType.DHUHR to "12:54",
                PrayerType.ASR to "16:13",
                PrayerType.MAGHRIB to "19:02",
                PrayerType.ISHA to "20:49"
            ),
            "Winter" to mapOf(
                PrayerType.FAJR to "05:59",
                PrayerType.SUNRISE to "08:04",
                PrayerType.DHUHR to "11:59",
                PrayerType.ASR to "13:38",
                PrayerType.MAGHRIB to "15:53",
                PrayerType.ISHA to "17:51"
            )
        )

        val dates = mapOf(
            "Spring" to makeDate(2024, 3, 21, "Europe/London"),
            "Autumn" to makeDate(2024, 9, 21, "Europe/London"),
            "Winter" to makeDate(2024, 12, 21, "Europe/London")
        )

        for ((season, date) in dates) {
            val times = PrayTimesCalculator.calculate(
                date, LONDON_LAT, LONDON_LNG, tz,
                method = CalculationMethod.MWL,
                highLatitudeMethod = HighLatitudeMethod.NONE
            )
            val expectedMap = goldenLondon[season]!!
            for ((type, expectedTime) in expectedMap) {
                val actualTime = times[type]!!.formattedTime
                assertGoldenTime(expectedTime, actualTime, type.name, "London", season)
            }
        }
    }
}
