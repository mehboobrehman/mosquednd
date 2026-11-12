package com.mosquednd.prayer

import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.Date

class GoldenDataGeneratorTest {
    @Test
    fun generateGoldenData() {
        val cities = listOf(
            Triple("Karachi", 24.8967, 67.2369),
            Triple("Makkah", 21.4225, 39.8262),
            Triple("London", 51.5074, -0.1278)
        )
        val dates = listOf(
            makeDate(2024, 3, 21),
            makeDate(2024, 6, 21),
            makeDate(2024, 12, 21)
        )
        val methods = listOf(
            CalculationMethod.KARACHI,
            CalculationMethod.UMM_AL_QURA,
            CalculationMethod.MWL
        )

        for (city in cities) {
            for (date in dates) {
                // Determine appropriate method for the city
                val method = when (city.first) {
                    "Karachi" -> CalculationMethod.KARACHI
                    "Makkah" -> CalculationMethod.UMM_AL_QURA
                    else -> CalculationMethod.MWL
                }
                val times = PrayTimesCalculator.calculate(
                    date, city.second, city.third,
                    method = method,
                    timeZone = TimeZone.getTimeZone("UTC") // Use UTC for stable results
                )
                println("City: ${city.first}, Date: ${date}, Method: $method")
                for (type in PrayerType.entries) {
                    println("${type}: ${times[type]?.formattedTime}")
                }
            }
        }
    }

    private fun makeDate(year: Int, month: Int, day: Int): Date {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, 12, 0, 0)
        return cal.time
    }
}
