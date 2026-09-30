package com.mosquednd.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mosquednd.R
import com.mosquednd.service.AdhanService
import com.mosquednd.ui.MainActivity
import com.mosquednd.util.PendingIntentConstants

object PrayerNotificationManager {
    const val CHANNEL_ID = "ongoing_prayer_countdown"
    const val NOTIFICATION_ID = 2000

    fun createPrayerCountdownNotification(context: Context, nextPrayerName: String, timeRemaining: String): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "prayer_times")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            PendingIntentConstants.NOTIFICATION_SERVICE_TAP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Next Prayer: $nextPrayerName")
            .setContentText("In $timeRemaining")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .build()
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prayer Countdown",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the time remaining until the next prayer"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
