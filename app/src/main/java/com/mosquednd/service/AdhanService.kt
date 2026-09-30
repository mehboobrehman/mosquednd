package com.mosquednd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mosquednd.R
import com.mosquednd.receiver.SilenceAlarmReceiver
import com.mosquednd.sound.AdhanPlayer
import com.mosquednd.ui.MainActivity
import com.mosquednd.ui.prayer.AdhanAlarmActivity
import com.mosquednd.util.LogHelper
import com.mosquednd.util.PendingIntentConstants
import java.util.concurrent.TimeUnit

/**
 * Foreground Service for robust Adhan audio playback and persistent prayer notification.
 * Uses a partial wake lock to keep the CPU awake during Adhan playback.
 */
class AdhanService : Service() {

    companion object {
        private const val TAG = "AdhanService"

        const val ACTION_PLAY_ADHAN = "com.mosquednd.action.PLAY_ADHAN"
        const val ACTION_STOP_ADHAN = "com.mosquednd.action.STOP_ADHAN"

        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_IS_ADHAN_MUTED = "is_adhan_muted"

        const val CHANNEL_ID = "prayer_alerts"
        const val NOTIFICATION_ID = 1000

        @Volatile
        var isAdhanPlaying: Boolean = false
            internal set

        /**
         * Sends an [ACTION_STOP_ADHAN] intent to [AdhanService] to gracefully stop Adhan playback.
         */
        @JvmStatic
        fun stopAdhan(context: Context) {
            val intent = Intent(context, AdhanService::class.java).apply {
                action = ACTION_STOP_ADHAN
            }
            try {
                context.startService(intent)
                LogHelper.logd(TAG, "Sent ACTION_STOP_ADHAN to AdhanService")
            } catch (e: Exception) {
                LogHelper.loge(TAG, "Error sending STOP_ADHAN to AdhanService", e)
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var adhanPlayer: AdhanPlayer? = null
    private var isReceiverRegistered = false

    private val silenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            LogHelper.logd(TAG, "silenceReceiver onReceive action: $action")
            if (action == "android.media.VOLUME_CHANGED_ACTION" || action == Intent.ACTION_SCREEN_OFF) {
                LogHelper.logd(TAG, "Hardware volume/screen-off event detected ($action) — silencing Adhan gracefully")
                stopAdhanInternal()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(silenceReceiver, filter)
        isReceiverRegistered = true
        LogHelper.logd(TAG, "AdhanService created and volume/screen-off receiver registered")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_ADHAN -> {
                val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
                val isMuted = intent.getBooleanExtra(EXTRA_IS_ADHAN_MUTED, false)
                LogHelper.logd(TAG, "ACTION_PLAY_ADHAN received for: $prayerName (muted=$isMuted)")
                LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "ADHAN_SERVICE_PLAY", mapOf("prayer" to prayerName, "muted" to isMuted))
                handlePlayAdhan(prayerName, isMuted)
            }
            ACTION_STOP_ADHAN -> {
                LogHelper.logd(TAG, "ACTION_STOP_ADHAN received")
                LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "ADHAN_SERVICE_STOP")
                stopAdhanInternal()
            }
            else -> {
                LogHelper.logw(TAG, "Unknown or null action in AdhanService: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun handlePlayAdhan(prayerName: String, isMuted: Boolean) {
        createNotificationChannel()

        val notification = createPrayerNotification(prayerName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        isAdhanPlaying = true

        if (isMuted) {
            LogHelper.logd(TAG, "Adhan is muted for $prayerName — skipping audio playback")
            // Stopped playback, but notification is shown. Release wake lock & stop foreground service safely.
            stopAdhanInternal()
            return
        }

        try {
            adhanPlayer?.stop()
            val player = AdhanPlayer(this).apply {
                onCompletion = {
                    LogHelper.logd(TAG, "Adhan playback completed naturally")
                    stopAdhanInternal()
                }
                onFocusLost = {
                    LogHelper.logd(TAG, "Adhan playback stopped due to focus loss")
                    stopAdhanInternal()
                }
            }
            player.playWithCustomUri(this)
            adhanPlayer = player
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Failed to start AdhanPlayer in AdhanService", e)
            stopAdhanInternal()
        }
    }

    private fun acquireWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MosqueDND:AdhanWakeLock").apply {
                acquire(TimeUnit.MINUTES.toMillis(5)) // 5 minute max wake lock
            }
            LogHelper.logd(TAG, "WakeLock acquired for Adhan playback (max 5 min)")
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun stopAdhanInternal() {
        LogHelper.logd(TAG, "Stopping AdhanService and releasing resources")
        try {
            adhanPlayer?.stop()
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Error stopping AdhanPlayer", e)
        } finally {
            adhanPlayer = null
        }

        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Error releasing WakeLock", e)
        } finally {
            wakeLock = null
        }

        isAdhanPlaying = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createPrayerNotification(prayerName: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "prayer_times")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            PendingIntentConstants.NOTIFICATION_SERVICE_TAP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val silenceIntent = Intent(this, SilenceAlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS
        }
        val silencePendingIntent = PendingIntent.getBroadcast(
            this,
            PendingIntentConstants.NOTIFICATION_ACTION_SILENCE,
            silenceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, SilenceAlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            PendingIntentConstants.NOTIFICATION_ACTION_DISMISS,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmIntent = Intent(this, AdhanAlarmActivity::class.java).apply {
            putExtra(AdhanAlarmActivity.EXTRA_PRAYER_NAME, prayerName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            PendingIntentConstants.NOTIFICATION_FULL_SCREEN,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val silenceAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Silence",
            silencePendingIntent
        ).build()

        val dismissAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Dismiss",
            dismissPendingIntent
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Prayer Time: $prayerName")
            .setContentText("Time for $prayerName prayer")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(silenceAction)
            .addAction(dismissAction)
            .setDeleteIntent(dismissPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_prayer_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_prayer_alerts_desc)
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            LogHelper.logd(TAG, "Notification channel '$CHANNEL_ID' created/verified")
        }
    }

    override fun onDestroy() {
        LogHelper.logd(TAG, "AdhanService destroyed")
        stopAdhanInternal()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(silenceReceiver)
            } catch (e: Exception) {
                LogHelper.loge(TAG, "Error unregistering silenceReceiver", e)
            }
            isReceiverRegistered = false
        }
        super.onDestroy()
    }
}