package com.mosquednd.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.mosquednd.sound.AdhanPlayer
import com.mosquednd.util.LogHelper

/**
 * BroadcastReceiver for prayer time alarms.
 * Triggered by AlarmManager when a scheduled prayer time arrives.
 * Delegates foreground playback and high-priority notification handling to [AdhanService].
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_IS_ADHAN_MUTED = "is_adhan_muted"
        const val CHANNEL_ID = "prayer_alerts"
        const val NOTIFICATION_ID = 1000

        const val ACTION_DISMISS = "com.mosquednd.action.DISMISS_ADHAN"
        const val ACTION_FORCE_SILENT = "com.mosquednd.action.FORCE_SILENT"

        @Volatile
        private var currentAdhanPlayer: AdhanPlayer? = null

        /**
         * Stops the currently playing Adhan audio (both in-process player and AdhanService).
         */
        @JvmStatic
        fun stopCurrentAdhan(context: Context? = null) {
            currentAdhanPlayer?.apply {
                stop()
                LogHelper.logd(TAG, "AdhanPlayer stopped via stopCurrentAdhan()")
            }
            currentAdhanPlayer = null
            context?.let { AdhanService.stopAdhan(it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        LogHelper.logd(TAG, "AlarmReceiver onReceive action: $action")

        if (ACTION_DISMISS == action || ACTION_FORCE_SILENT == action) {
            LogHelper.logd(TAG, "Dismiss/Force-silent action received — stopping AdhanService")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "ADHAN_DISMISSED", mapOf("action" to action))
            AdhanService.stopAdhan(context)
            stopCurrentAdhan(context)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
        val isMuted = intent.getBooleanExtra(EXTRA_IS_ADHAN_MUTED, false)
        LogHelper.logd(TAG, "Prayer alarm triggered for: $prayerName (muted=$isMuted)")
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "PRAYER_ALARM_TRIGGERED", mapOf("prayer" to prayerName, "muted" to isMuted))

        // Delegate foreground playback and notification to AdhanService
        val serviceIntent = Intent(context, AdhanService::class.java).apply {
            this.action = AdhanService.ACTION_PLAY_ADHAN
            putExtra(AdhanService.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AdhanService.EXTRA_IS_ADHAN_MUTED, isMuted)
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            LogHelper.logd(TAG, "Started AdhanService for $prayerName")
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Failed to start AdhanService for $prayerName", e)
        }
    }
}