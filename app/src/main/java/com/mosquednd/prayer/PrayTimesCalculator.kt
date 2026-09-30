package com.mosquednd.prayer

import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan

/**
 * Core prayer time calculation engine.
 * Ports the standard PrayTimes algorithm (http://praytimes.org/calculation/) to Kotlin.
 * Pure JVM math -- no Android dependencies.
 */
object PrayTimesCalculator {

    /**
     * Calculate prayer times for a given date, location, and method.
     */
    fun calculate(
        date: Date,
        latitude: Double,
        longitude: Double,
        timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
        method: CalculationMethod = CalculationMethod.MWL,
        asrMethod: AsrMethod = AsrMethod.SHAFII,
        highLatitudeMethod: HighLatitudeMethod = HighLatitudeMethod.NONE,
        offsets: Map<PrayerType, Int> = emptyMap()
    ): Map<PrayerType, PrayerTime> {
        val cal = Calendar.getInstance(timeZone)
        cal.time = date

        val tzOffsetHours = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 3_600_000.0

        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val jd = julianDate(year, month, day)

        val D = jd - 2451545.0
        val dec = sunDeclination(D)
        val eqt = equationOfTime(D)

        val dhuhr = 12.0 + tzOffsetHours - longitude / 15.0 - eqt

        val sunriseHA = sunHourAngle(-0.8333, latitude, dec)
        val sunrise = dhuhr - sunriseHA
        val sunset = dhuhr + sunriseHA

        var fajr = dhuhr - sunHourAngle(-method.angles.fajrAngle, latitude, dec)

        var isha: Double
        if (method.angles.ishaMinutes > 0) {
            isha = sunset + method.angles.ishaMinutes / 60.0
        } else {
            isha = dhuhr + sunHourAngle(-method.angles.ishaAngle, latitude, dec)
        }

        val maghrib: Double = if (method.angles.maghribAngle > 0) {
            dhuhr + sunHourAngle(-method.angles.maghribAngle, latitude, dec)
        } else {
            sunset
        }

        val asrHA = asrHourAngle(latitude, dec, asrMethod)
        val asr = dhuhr + asrHA

        val nightHours = 24.0 - (sunset - sunrise)

        if (highLatitudeMethod != HighLatitudeMethod.NONE && abs(latitude) >= 48.0) {
            when (highLatitudeMethod) {
                HighLatitudeMethod.MIDDLE_OF_NIGHT -> {
                    val nightPortion = nightHours / 2.0
                    fajr = sunrise - nightPortion
                    isha = sunset + nightPortion
                }
                HighLatitudeMethod.ANGLE_BASED -> {
                    val fajrPortion = nightHours * (method.angles.fajrAngle / 60.0)
                    val ishaAngle = if (method.angles.ishaMinutes > 0) method.angles.fajrAngle else method.angles.ishaAngle
                    val ishaPortion = nightHours * (ishaAngle / 60.0)
                    fajr = sunrise - fajrPortion
                    isha = sunset + ishaPortion
                }
                HighLatitudeMethod.NEAREST_LATITUDE -> {
                    val targetLat = if (latitude > 0) 48.0 else -48.0
                    fajr = dhuhr - sunHourAngle(-method.angles.fajrAngle, targetLat, dec)
                    if (method.angles.ishaMinutes > 0) {
                        val targetSunset = dhuhr + sunHourAngle(-0.8333, targetLat, dec)
                        isha = targetSunset + method.angles.ishaMinutes / 60.0
                    } else {
                        isha = dhuhr + sunHourAngle(-method.angles.ishaAngle, targetLat, dec)
                    }
                }
                else -> { /* NONE */ }
            }
        }

        val rawTimes = mapOf(
            PrayerType.FAJR to fajr,
            PrayerType.SUNRISE to sunrise,
            PrayerType.DHUHR to dhuhr,
            PrayerType.ASR to asr,
            PrayerType.MAGHRIB to maghrib,
            PrayerType.ISHA to isha
        )

        val offsetTimes = rawTimes.mapValues { (type, hours) ->
            val offsetMin = offsets[type] ?: 0
            hours + offsetMin / 60.0
        }

        val result = mutableMapOf<PrayerType, PrayerTime>()
        for ((type, hours) in offsetTimes) {
            val prayerCal = Calendar.getInstance(timeZone)
            prayerCal.time = date
            val totalMinutes = round(hours * 60.0).toInt()
            val normalized = ((totalMinutes % 1440) + 1440) % 1440
            val h = normalized / 60
            val m = normalized % 60
            prayerCal.set(Calendar.HOUR_OF_DAY, h)
            prayerCal.set(Calendar.MINUTE, m)
            prayerCal.set(Calendar.SECOND, 0)
            prayerCal.set(Calendar.MILLISECOND, 0)

            result[type] = PrayerTime(
                type = type,
                time = prayerCal,
                formattedTime = String.format("%02d:%02d", h, m)
            )
        }

        return result
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val A = floor(y / 100.0).toInt()
        val B = 2 - A + A / 4
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + B - 1524.5
    }

    private fun sunDeclination(D: Double): Double {
        val g = fixAngle(357.529 + 0.98560028 * D)
        val q = fixAngle(280.459 + 0.98564736 * D)
        val L = fixAngle(q + 1.915 * sin(toRad(g)) + 0.020 * sin(toRad(2 * g)))
        val e = 23.439 - 0.00000036 * D
        return toDeg(asin(sin(toRad(e)) * sin(toRad(L))))
    }

    private fun equationOfTime(D: Double): Double {
        val g = fixAngle(357.529 + 0.98560028 * D)
        val q = fixAngle(280.459 + 0.98564736 * D)
        val L = fixAngle(q + 1.915 * sin(toRad(g)) + 0.020 * sin(toRad(2 * g)))
        val e = 23.439 - 0.00000036 * D
        val RA = fixHour(toDeg(atan2(cos(toRad(e)) * sin(toRad(L)), cos(toRad(L)))) / 15.0)
        return q / 15.0 - RA
    }

    private fun sunHourAngle(angle: Double, lat: Double, dec: Double): Double {
        val latRad = toRad(lat)
        val decRad = toRad(dec)
        val cosHA = (sin(toRad(angle)) - sin(latRad) * sin(decRad)) /
                (cos(latRad) * cos(decRad))
        return when {
            cosHA < -1.0 -> 0.0
            cosHA > 1.0 -> 0.0
            else -> toDeg(acos(cosHA)) / 15.0
        }
    }

    private fun asrHourAngle(lat: Double, dec: Double, asrMethod: AsrMethod): Double {
        val latRad = toRad(lat)
        val decRad = toRad(dec)
        val factor = asrMethod.shadowFactor
        val angle = atan(1.0 / (factor + tan(abs(latRad - decRad))))
        val cosHA = (sin(angle) - sin(latRad) * sin(decRad)) /
                (cos(latRad) * cos(decRad))
        return when {
            cosHA < -1.0 -> 0.0
            cosHA > 1.0 -> 0.0
            else -> toDeg(acos(cosHA)) / 15.0
        }
    }

    private fun toRad(deg: Double): Double = deg * Math.PI / 180.0
    private fun toDeg(rad: Double): Double = rad * 180.0 / Math.PI
    private fun fixAngle(a: Double): Double = a - 360.0 * floor(a / 360.0)
    private fun fixHour(h: Double): Double = h - 24.0 * floor(h / 24.0)
}