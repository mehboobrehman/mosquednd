package com.mosquednd.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mosquednd.MosqueDNDApp
import com.mosquednd.prayer.AsrMethod
import com.mosquednd.prayer.CalculationMethod
import com.mosquednd.prayer.PrayTimesCalculator
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerType
import com.mosquednd.service.AlarmScheduler
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.PendingIntentConstants
import com.mosquednd.util.LogHelper
import java.util.Calendar

/**
 * Shared helper that recalculates today's prayer times using current settings
 * and re-schedules alarms for enabled prayers. Also schedules the daily midnight
 * self-triggering alarm so that prayer times are refreshed each day.
 */
object DailyRecalculationHelper {

    private const val TAG = "DailyRecalcHelper"

    /**
     * Full recalculation + reschedule cycle:
     * 1. Reads current location, calculation method, Asr method, and per-prayer offsets
     * 2. Calculates today's prayer times
     * 3. Cancels existing alarms and reschedules for enabled prayers
     * 4. Schedules the next midnight alarm to keep the chain alive
     */
    fun recalculateAndReschedule(context: Context) {
        // Skip full reschedule if master alarm is OFF — only recalculate for internal state
        if (!PrayerAlarmPrefs.isMasterAlarmEnabled()) {
            Log.w(TAG, "Master alarm OFF — skipping alarm reschedule")
            // Still schedule the midnight alarm for hijri date refresh at day change
            scheduleNextMidnightAlarm(context)
            return
        }

        val (lat, lng) = LocationProvider.getLastKnownLocation()
        LogHelper.logd(TAG, "Location: [REDACTED]")

        val prefs = MosqueDNDApp.prefs
        val calcMethodStr = prefs.getString("calculation_method", "mwl") ?: "mwl"
        val asrMethodStr = prefs.getString("asr_method", "standard") ?: "standard"
        val calcMethod = CalculationMethod.fromPreferenceValue(calcMethodStr)
        val asrMethod = AsrMethod.fromPreferenceValue(asrMethodStr)
        val offsets = PrayerAlarmPrefs.toOffsetsMap()

        Log.d(TAG, "Calculating with method=$calcMethod, asr=$asrMethod, offsets=$offsets")

        val now = Calendar.getInstance()
        val timesMap = PrayTimesCalculator.calculate(
            date = now.time,
            latitude = lat,
            longitude = lng,
            method = calcMethod,
            asrMethod = asrMethod,
            offsets = offsets
        )

        val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes().toSet()
        Log.d(TAG, "Enabled prayers: $enabledTypes")

        AlarmScheduler.rescheduleAll(context, timesMap, enabledTypes)

        // Schedule DND alarms alongside prayer alarms
        val dndEnabledTypes = PrayerType.entries.filter { it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it) }.toSet()
        Log.d(TAG, "DND enabled prayers: $dndEnabledTypes")
        AlarmScheduler.cancelDNDAlarms(context)
        AlarmScheduler.scheduleDNDAlarms(context, timesMap, dndEnabledTypes)

        // Keep the daily midnight chain alive
        scheduleNextMidnightAlarm(context)

        Log.d(TAG, "Recalculation and reschedule complete")
    }

    /**
     * Schedules (or re-schedules) a single exact alarm at 00:01.
     * Uses setExactAndAllowWhileIdle so the alarm fires at the exact time
     * even under Doze. MidnightAlarmReceiver.onReceive() calls recalculateAndReschedule()
     * which calls this method again, forming a self-sustaining chain of exact midnight ticks.
     * The PendingIntent targets MidnightAlarmReceiver which will recalculate
     * and re-schedule itself.
     */
    fun scheduleNextMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: run {
                Log.w(TAG, "AlarmManager not available, cannot schedule midnight alarm")
                return
            }

        val intent = Intent(context, MidnightAlarmReceiver::class.java).apply {
            // No action string — MidnightAlarmReceiver uses a null action check
            // to identify "midnight tick" versus system broadcasts
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PendingIntentConstants.MIDNIGHT_RECALCULATION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next trigger at 00:01 tomorrow, then repeat every 24 hours
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMidnight.timeInMillis,
            pendingIntent
        )
        Log.d(TAG, "Daily midnight alarm scheduled at ${nextMidnight.time}")
    }
}
