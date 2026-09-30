package com.mosquednd.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mosquednd.service.AdhanService
import com.mosquednd.service.AlarmReceiver
import com.mosquednd.ui.MainActivity

/**
 * BroadcastReceiver that silences the prayer alarm when the user taps the notification or action buttons.
 *
 * Flow on action button tap:
 * 1. Stops any currently playing Adhan audio (via AlarmReceiver.stopCurrentAdhan and AdhanService.stopAdhan)
 * 2. Dismisses the prayer alarm notification
 * 3. Does NOT open the app (non-disruptive)
 *
 * Flow on notification content tap:
 * 1. Stops Adhan audio
 * 2. Dismisses notification
 * 3. Opens the app to the main screen
 *
 * Manifest-declared so it works even when the app is backgrounded or killed.
 */
class SilenceAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SilenceAlarmReceiver"

        /** Extra key for the prayer name passed from the notification PendingIntent. */
        const val EXTRA_PRAYER_NAME = "prayer_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Unknown"
        val action = intent.action
        Log.d(TAG, "Silence/Dismiss request received for prayer: $prayerName, action: $action")

        // Step 1: Stop any currently playing Adhan audio.
        AdhanService.stopAdhan(context)
        AlarmReceiver.stopCurrentAdhan(context)
        Log.d(TAG, "Adhan playback stopped for: $prayerName")

        // Step 2: Dismiss the prayer alarm notification.
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(AlarmReceiver.NOTIFICATION_ID)
        Log.d(TAG, "Prayer notification dismissed")

        // Step 3: Open the app only if not triggered via background quick actions (Silence/Dismiss)
        if (AlarmReceiver.ACTION_DISMISS != action && AlarmReceiver.ACTION_FORCE_SILENT != action) {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(openAppIntent)
            Log.d(TAG, "App launched to MainActivity after tap on notification")
        } else {
            Log.d(TAG, "Background action completed without opening the app")
        }
    }
}