package com.mosquednd.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mosquednd.util.LogHelper

/**
 * BroadcastReceiver that recalculates prayer times and reschedules alarms
 * on key system events and at daily midnight.
 *
 * Listens for:
 * - [Intent.ACTION_TIMEZONE_CHANGED] — user changed timezone
 * - [Intent.ACTION_TIME_CHANGED] — user set time manually
 *
 * The receiver also handles its own scheduled "midnight tick" (no action set)
 * which triggers a daily recalibration cycle.
 *
 * Note: BOOT_COMPLETED is handled exclusively by [BootReceiver] to avoid
 * duplicate work on every boot.
 */
class MidnightAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MidnightAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            Log.w(TAG, "Received null intent")
            return
        }

        val action = intent.action
        Log.d(TAG, "Received broadcast: ${action ?: "(midnight tick)"}")
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "MIDNIGHT_ALARM_RECEIVED", mapOf("action" to (action ?: "midnight_tick")))

        when (action) {
            // Timezone manually changed — recalculate
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d(TAG, "Timezone changed — recalculating prayer times")
                DailyRecalculationHelper.recalculateAndReschedule(context)
            }

            // User set time manually — recalculate
            Intent.ACTION_TIME_CHANGED -> {
                Log.d(TAG, "Time changed — recalculating prayer times")
                DailyRecalculationHelper.recalculateAndReschedule(context)
            }

            // Null action = our own midnight tick alarm from AlarmManager
            null -> {
                Log.d(TAG, "Midnight tick — recalculating prayer times for new day")
                DailyRecalculationHelper.recalculateAndReschedule(context)
            }

            // Unknown action — log and ignore
            else -> {
                Log.w(TAG, "Ignoring unknown broadcast action: $action")
            }
        }
    }
}
