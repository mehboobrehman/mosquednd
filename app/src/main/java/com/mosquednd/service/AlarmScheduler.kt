package com.mosquednd.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerTime
import com.mosquednd.prayer.PrayerType
import com.mosquednd.receiver.DNDAlarmReceiver
import com.mosquednd.ui.MainActivity
import com.mosquednd.util.PendingIntentConstants
import java.util.Calendar

/**
 * Schedules and cancels prayer alarm intents via AlarmManager.
 * Uses setAlarmClock for maximum precision and Doze/Battery Saver bypass,
 * with setExactAndAllowWhileIdle and setWindow fallbacks for safety.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    @Volatile
    private var alarmScheduleErrorCallback: ((String) -> Unit)? = null

    /** Register a callback invoked when an alarm fails to schedule. */
    fun setAlarmScheduleErrorCallback(callback: ((String) -> Unit)?) {
        alarmScheduleErrorCallback = callback
    }

    /** Returns true if the given Calendar time is in the future (strictly after now). */
    private fun isTimeInFuture(calendar: Calendar): Boolean {
        return calendar.after(Calendar.getInstance())
    }

    /**
     * Schedules a single prayer alarm.
     *
     * Uses setAlarmClock (highest priority Android alarm API that bypasses Doze throttling)
     * with fallbacks to setExactAndAllowWhileIdle / setWindow if needed.
     *
     * @param context Application context
     * @param prayerType The prayer type to schedule
     * @param prayerName Name of the prayer (e.g. "Fajr", "Dhuhr") — used in the broadcast intent
     * @param requestCode Unique request code for this alarm (used for cancellation)
     * @param triggerMillisOverride Epoch millis for the alarm trigger. Must not be null.
     */
    fun schedulePrayerAlarm(
        context: Context,
        prayerType: PrayerType,
        prayerName: String,
        requestCode: Int,
        triggerMillisOverride: Long? = null
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: run {
                Log.e(TAG, "AlarmManager service not available")
                return
            }

        if (prayerType == PrayerType.SUNRISE) {
            Log.w(TAG, "Skipping [${prayerType.name}] — sunrise has no adhan")
            return
        }

        val triggerMillis = triggerMillisOverride
            ?: run {
                Log.w(TAG, "Skipping [$prayerName] — triggerMillisOverride is null")
                return
            }

        val triggerCal = Calendar.getInstance().apply { timeInMillis = triggerMillis }

        // Guard: never schedule an alarm in the past — it would fire immediately
        if (!isTimeInFuture(triggerCal)) {
            Log.w(TAG, "Skipping [$prayerName] — time has already passed today (trigger=$triggerMillis, now=${System.currentTimeMillis()})")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AlarmReceiver.EXTRA_IS_ADHAN_MUTED, PrayerAlarmPrefs.isAdhanMuted(prayerType))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create PendingIntent for AlarmClockInfo that launches MainActivity when alarm clock is tapped
        val showIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "prayer_times")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode + 1000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled [$prayerName] via setAlarmClock (req=$requestCode, trigger=$triggerMillis)")
        } catch (e: Exception) {
            Log.w(TAG, "setAlarmClock failed for [$prayerName], trying setExactAndAllowWhileIdle fallback", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled [$prayerName] via setExactAndAllowWhileIdle fallback (req=$requestCode)")
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled [$prayerName] via setExact fallback (req=$requestCode)")
                }
            } catch (e2: Exception) {
                Log.w(TAG, "setExactAndAllowWhileIdle failed for [$prayerName], trying setWindow fallback", e2)
                try {
                    val windowStart = System.currentTimeMillis()
                    val windowLength = maxOf(0L, triggerMillis - windowStart)
                    if (windowLength > 0) {
                        alarmManager.setWindow(
                            AlarmManager.RTC_WAKEUP,
                            windowStart,
                            windowLength,
                            pendingIntent
                        )
                        Log.d(TAG, "Scheduled [$prayerName] via setWindow fallback (req=$requestCode)")
                    } else {
                        Log.w(TAG, "Cannot fallback to setWindow — trigger time already passed")
                    }
                } catch (e3: Exception) {
                    val msg = "Failed to schedule alarm for $prayerName at $triggerMillis (all fallbacks failed)"
                    Log.e(TAG, msg, e3)
                    alarmScheduleErrorCallback?.invoke(msg)
                }
            }
        }
    }

    /**
     * Cancels a previously scheduled prayer alarm by its request code.
     */
    fun cancelAlarm(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: run {
                Log.e(TAG, "AlarmManager service not available")
                return
            }

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled alarm (req=$requestCode)")
    }

    /**
     * Cancels all existing alarms, then schedules alarms for every prayer time.
     * Each prayer is assigned its index in the list as the request code.
     *
     * Self-contained: resolves trigger millis directly from [prayerTimes] with no cache.
     *
     * @param context Application context
     * @param prayerTimes List of Calendar objects, one per prayer
     * @param prayerNames List of prayer name strings, must match prayerTimes size
     */
    fun scheduleAllPrayers(
        context: Context,
        prayerTimes: List<Calendar>,
        prayerNames: List<String>
    ) {
        if (prayerTimes.size != prayerNames.size) {
            Log.e(TAG, "prayerTimes.size (${prayerTimes.size}) != prayerNames.size (${prayerNames.size})")
            return
        }

        // Build a local times map directly from the provided calendars — no external cache
        val timesMap = mutableMapOf<PrayerType, Calendar>()
        for (i in prayerTimes.indices) {
            val name = prayerNames[i]
            val type = PrayerType.entries.find { it.displayName.equals(name, ignoreCase = true) }
            if (type != null) {
                timesMap[type] = prayerTimes[i]
            }
        }

        cancelAllPrayers(context, prayerTimes.size)

        Log.d(TAG, "Scheduling ${prayerTimes.size} prayers")
        for (i in prayerTimes.indices) {
            val name = prayerNames[i]
            val type = PrayerType.entries.find { it.displayName.equals(name, ignoreCase = true) }
            if (type == null) {
                Log.w(TAG, "Unknown prayer name '$name' — skipping")
                continue
            }

            if (type == PrayerType.SUNRISE) {
                Log.w(TAG, "Skipping [${type.name}] — sunrise has no adhan")
                continue
            }

            val triggerMillis = timesMap[type]?.timeInMillis
            if (triggerMillis == null) {
                Log.w(TAG, "Skipping [${type.name}] — no trigger time in provided prayerTimes")
                continue
            }

            schedulePrayerAlarm(
                context = context,
                prayerType = type,
                prayerName = type.displayName,
                requestCode = PendingIntentConstants.PRAYER_ALARM_BASE + type.ordinal,
                triggerMillisOverride = triggerMillis
            )
        }
    }

    /**
     * Cancels all prayer alarms with request codes [PendingIntentConstants.PRAYER_ALARM_BASE]..[PendingIntentConstants.PRAYER_ALARM_BASE]+count-1.
     *
     * @param context Application context
     * @param count Number of alarms to cancel (default 5 for the five daily prayers)
     */
    fun cancelAllPrayers(context: Context, count: Int = 5) {
        for (i in 0 until count) {
            cancelAlarm(context, PendingIntentConstants.PRAYER_ALARM_BASE + i)
        }
        Log.d(TAG, "Cancelled all $count prayer alarms")
    }

    /**
     * Cancels all existing prayer alarms and re-schedules only the enabled ones.
     * Sunrise is never scheduled even if present in enabledTypes.
     *
     * Fully self-contained: resolves each prayer time directly from [timesMap].
     *
     * @param context Application context
     * @param timesMap Map of prayer type to its calculated PrayerTime
     * @param enabledTypes Set of prayer types that should have alarms enabled
     */
    fun rescheduleAll(
        context: Context,
        timesMap: Map<PrayerType, PrayerTime>,
        enabledTypes: Set<PrayerType>
    ) {
        if (enabledTypes.isEmpty()) {
            Log.w(TAG, "rescheduleAll called with empty enabledTypes — no alarms to schedule")
            cancelAllPrayers(context, PrayerType.entries.size)
            return
        }

        // Cancel all alarms for all prayer request codes
        cancelAllPrayers(context, PrayerType.entries.size)

        Log.d(TAG, "Rescheduling alarms for enabled prayers: $enabledTypes")
        for (type in enabledTypes) {
            // Never schedule Sunrise even if somehow enabled
            if (type == PrayerType.SUNRISE) continue

            val resolvedMillis = timesMap[type]?.time?.timeInMillis
            if (resolvedMillis == null) {
                Log.w(TAG, "Skipping ${type.displayName} — not in timesMap")
                continue
            }

            val resolvedCal = Calendar.getInstance().apply { timeInMillis = resolvedMillis }
            if (!isTimeInFuture(resolvedCal)) {
                Log.w(TAG, "Skipping ${type.displayName} — time has already passed today")
                continue
            }

            schedulePrayerAlarm(
                context = context,
                prayerType = type,
                prayerName = type.displayName,
                requestCode = PendingIntentConstants.PRAYER_ALARM_BASE + type.ordinal,
                triggerMillisOverride = resolvedMillis
            )
        }
    }

    // ──────────────────────────────────────────────
    // DND Alarm Scheduling
    // ──────────────────────────────────────────────

    @Suppress("UNUSED_PARAMETER")
    /**
     * Schedules DND START and DND END alarms for all enabled prayer types.
     *
     * For each enabled prayer (SUNRISE excluded):
     * - startTime = prayerTime + offset (from PrayerAlarmPrefs.getDNDOffset)
     * - endTime   = startTime + duration (from PrayerAlarmPrefs.getDNDDuration)
     *
     * Both alarms use setExactAndAllowWhileIdle. Cancels any existing DND alarms
     * before re-scheduling.
     *
     * @param context Application context
     * @param timesMap Map of prayer type to its calculated PrayerTime
     * @param enabledTypes Set of prayer types that have DND enabled
     */
    fun scheduleDNDAlarms(
        context: Context,
        timesMap: Map<PrayerType, PrayerTime>,
        enabledTypes: Set<PrayerType>
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: run {
                Log.e(TAG, "AlarmManager service not available")
                return
            }

        // Cancel existing DND alarms before re-scheduling
        cancelDNDAlarms(context)

        var scheduledCount = 0
        for (type in enabledTypes) {
            if (type == PrayerType.SUNRISE) continue
            if (PrayerAlarmPrefs.isAdhanMuted(type)) {
                Log.d(TAG, "Skipping DND for $type — adhan muted")
                continue
            }

            val prayerTime = timesMap[type]?.time ?: continue
            val offset = PrayerAlarmPrefs.getDNDOffset(type)
            val duration = PrayerAlarmPrefs.getDNDDuration(type)

            val alarmOffset = PrayerAlarmPrefs.getOffset(type)
            val rawMs = prayerTime.timeInMillis - (alarmOffset * 60 * 1000)
            val startCal = Calendar.getInstance().apply { timeInMillis = rawMs }
            startCal.add(Calendar.MINUTE, offset)

            // Skip prayers whose start window has already passed — don't schedule retroactive DND windows
            if (!isTimeInFuture(startCal)) {
                Log.w(TAG, "Skipping DND for $type — start window has passed")
                continue
            }

            val endCal = Calendar.getInstance().apply { timeInMillis = startCal.timeInMillis }
            endCal.add(Calendar.MINUTE, duration)

            // Clamp DND end time to same day — don't roll into next day (e.g. 23:50 + 30min → 00:20)
            val startDay = startCal.get(Calendar.DAY_OF_YEAR)
            val endDay = endCal.get(Calendar.DAY_OF_YEAR)
            if (endDay != startDay) {
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 0)
                Log.w(TAG, "scheduleDNDAlarms: endCal for $type crossed midnight, clamped to ${endCal.time}")
            }

            val startPendingIntent = DNDAlarmReceiver.getStartPendingIntent(context, type)
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    startCal.timeInMillis,
                    startPendingIntent
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to schedule DND start alarm for $type: ${e.message}", e)
                continue
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid DND start alarm for $type: ${e.message}", e)
                continue
            }

            val endPendingIntent = DNDAlarmReceiver.getEndPendingIntent(context, type)
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endCal.timeInMillis,
                    endPendingIntent
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to schedule DND end alarm for $type: ${e.message}", e)
                continue
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid DND end alarm for $type: ${e.message}", e)
                continue
            }

            scheduledCount++
            Log.d(TAG, "Scheduled DND for $type: start=$offset min end=+${offset + duration} min")
        }

        Log.d(TAG, "Scheduled DND alarms for $scheduledCount prayers")
    }

    /**
     * Cancels all DND alarms for all prayer types (5 prayers × 2 alarms = 10).
     * Iterates all [PrayerType] entries except SUNRISE.
     */
    fun cancelDNDAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: run {
                Log.e(TAG, "AlarmManager service not available")
                return
            }

        var cancelledCount = 0
        for (type in PrayerType.entries) {
            if (type == PrayerType.SUNRISE) continue
            cancelDNDAlarmInternal(context, alarmManager, type)
            cancelledCount++
        }
        Log.d(TAG, "Cancelled DND alarms for $cancelledCount prayers")
    }

    /**
     * Cancels DND START and DND END alarms for a specific prayer type.
     * Safe to call even if no alarms are currently scheduled.
     */
    fun cancelDNDAlarm(context: Context, type: PrayerType) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: run {
                Log.e(TAG, "AlarmManager service not available")
                return
            }
        cancelDNDAlarmInternal(context, alarmManager, type)
        Log.d(TAG, "Cancelled DND alarms for $type")
    }

    /**
     * Internal helper: cancels both START and END PendingIntents for a single prayer type.
     * Uses [DNDAlarmReceiver.getStartPendingIntent] and [DNDAlarmReceiver.getEndPendingIntent]
     * to create matching PendingIntents for cancellation.
     */
    private fun cancelDNDAlarmInternal(context: Context, alarmManager: AlarmManager, type: PrayerType) {
        val startPendingIntent = DNDAlarmReceiver.getStartPendingIntent(context, type)
        alarmManager.cancel(startPendingIntent)

        val endPendingIntent = DNDAlarmReceiver.getEndPendingIntent(context, type)
        alarmManager.cancel(endPendingIntent)
    }
}