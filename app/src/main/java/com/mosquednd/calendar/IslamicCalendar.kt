package com.mosquednd.calendar

import com.mosquednd.hijri.HijriConverter
import com.mosquednd.hijri.HijriDate
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.util.LogHelper
import java.util.Calendar

/**
 * Convenience wrapper around HijriConverter for Islamic calendar operations.
 * Provides current Hijri date lookup and formatted display strings.
 */
object IslamicCalendar {

    private const val TAG = "IslamicCalendar"

    private val monthNames = arrayOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )

    /**
     * Returns today's Hijri date based on the system clock, adjusted by the
     * user-configured offset (-1, 0, or +1 day) from SharedPreferences.
     * The offset is applied to the Gregorian date BEFORE conversion to Hijri,
     * so month/year rollover is handled automatically.
     */
    fun getCurrentHijriDate(): HijriDate {
        val now = Calendar.getInstance()

        // Read and apply Hijri date offset from preferences (clamped to -2..+2 by PrayerAlarmPrefs)
        val offset = PrayerAlarmPrefs.getHijriDateOffset()
        if (offset != 0) {
            now.add(Calendar.DAY_OF_MONTH, offset)
            logDebug("Applied Hijri offset of $offset day(s)")
        }

        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        val hijri = HijriConverter.gregorianToHijri(year, month, day)
        logDebug("Current Hijri date: $hijri")
        return hijri
    }

    /**
     * Formats a HijriDate as a human-readable string.
     * Example: "29 Ramadan 1445 AH"
     */
    fun format(date: HijriDate): String {
        val monthName = if (date.month in 1..12) monthNames[date.month - 1] else "?"
        return "${date.day} $monthName ${date.year} AH"
    }

    /**
     * Safe logging that works in both Android runtime and JVM test environments.
     * On JVM, `android.util.Log` stubs throw RuntimeException, so we catch silently.
     */
    private fun logDebug(message: String) {
        try {
            LogHelper.logd(TAG, message)
        } catch (_: RuntimeException) {
            // Silently ignore — Log stubs on JVM throw RuntimeException("Stub!")
        }
    }
}
