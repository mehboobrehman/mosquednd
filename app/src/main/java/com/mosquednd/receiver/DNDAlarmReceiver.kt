package com.mosquednd.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mosquednd.prayer.PrayerDNDManager
import com.mosquednd.prayer.PrayerType
import com.mosquednd.util.LogHelper
import com.mosquednd.util.PendingIntentConstants

/**
 * BroadcastReceiver that handles per-prayer DND start and end alarms.
 *
 * Fired by AlarmManager alarms scheduled via [com.mosquednd.service.AlarmScheduler].
 * Delegates to [PrayerDNDManager.onDNDStart] and [PrayerDNDManager.onDNDEnd]
 * which handle reference-counting and actual DND enable/disable via DNDController.
 *
 * Manifest-declared — not registered dynamically.
 */
class DNDAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            Log.w(TAG, "onReceive called with null intent")
            return
        }

        val action = intent.action
        if (action == null) {
            Log.w(TAG, "onReceive called with null action")
            return
        }

        val prayerType = extractPrayerType(intent)
        if (prayerType == null) {
            Log.e(TAG, "onReceive: invalid or missing prayer type extra for action=$action")
            return
        }

        Log.d(TAG, "onReceive: action=$action type=${prayerType.displayName}")
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "DND_ALARM_RECEIVED", mapOf("action" to action, "type" to prayerType.displayName))

        when (action) {
            ACTION_DND_START -> {
                PrayerDNDManager.onDNDStart(prayerType, context)
            }
            ACTION_DND_END -> {
                PrayerDNDManager.onDNDEnd(prayerType, context)
            }
            else -> {
                Log.w(TAG, "onReceive: unknown action=$action")
            }
        }
    }

    companion object {
        private const val TAG = "DNDAlarmReceiver"

        /** Action: DND window start (fired at prayer_time + offset). */
        const val ACTION_DND_START = "com.mosquednd.action.DND_START"

        /** Action: DND window end (fired at prayer_time + offset + duration). */
        const val ACTION_DND_END = "com.mosquednd.action.DND_END"

        /** Intent extra key for PrayerType.ordinal. */
        const val EXTRA_PRAYER_TYPE = "prayer_type"

        /**
         * Returns a [PendingIntent] that fires [ACTION_DND_START] for the given prayer type.
         * Request code: [PendingIntentConstants.DND_ALARM_START_BASE] + type.ordinal.
         */
        fun getStartPendingIntent(context: Context, type: PrayerType): PendingIntent {
            return createPendingIntent(
                context = context,
                action = ACTION_DND_START,
                requestCode = PendingIntentConstants.DND_ALARM_START_BASE + type.ordinal,
                type = type
            )
        }

        /**
         * Returns a [PendingIntent] that fires [ACTION_DND_END] for the given prayer type.
         * Request code: [DND_END_BASE] + type.ordinal.
         */
        fun getEndPendingIntent(context: Context, type: PrayerType): PendingIntent {
            return createPendingIntent(
                context = context,
                action = ACTION_DND_END,
                requestCode = PendingIntentConstants.DND_ALARM_END_BASE + type.ordinal,
                type = type
            )
        }

        /**
         * Creates a [PendingIntent] for the given action and prayer type.
         * Used by both [getStartPendingIntent] and [getEndPendingIntent].
         * Same method can be used for cancellation since Intent.filterEquals()
         * does not consider extras — only action + component + requestCode matter.
         */
        private fun createPendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
            type: PrayerType
        ): PendingIntent {
            val intent = Intent(context, DNDAlarmReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_PRAYER_TYPE, type.ordinal)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Extracts [PrayerType] from the intent's [EXTRA_PRAYER_TYPE] ordinal extra.
         * Returns null if the extra is missing or the ordinal is out of range.
         */
        private fun extractPrayerType(intent: Intent): PrayerType? {
            if (!intent.hasExtra(EXTRA_PRAYER_TYPE)) return null
            val ordinal = intent.getIntExtra(EXTRA_PRAYER_TYPE, -1)
            if (ordinal < 0 || ordinal >= PrayerType.entries.size) return null
            return PrayerType.entries[ordinal]
        }
    }
}
