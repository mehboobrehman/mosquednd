package com.mosquednd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserManager
import androidx.core.app.NotificationCompat
import com.mosquednd.R
import com.mosquednd.MosqueDNDApp
import com.mosquednd.prayer.AsrMethod
import com.mosquednd.prayer.CalculationMethod
import com.mosquednd.prayer.PrayTimesCalculator
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerType
import com.mosquednd.ui.MainActivity
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.LogHelper
import com.mosquednd.util.PendingIntentConstants
import java.util.Calendar

class PersistentMonitoringService : Service() {

    companion object {
        private const val TAG = "PersistentMonitoring"
        const val CHANNEL_ID = "mosque_persistent_monitoring"
        const val NOTIFICATION_ID = 2000

        fun startService(context: Context) {
            val intent = Intent(context, PersistentMonitoringService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                LogHelper.logd(TAG, "PersistentMonitoringService start request sent")
            } catch (e: Exception) {
                LogHelper.loge(TAG, "Failed to start PersistentMonitoringService", e)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PersistentMonitoringService::class.java)
            context.stopService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            // Schedule next update in 1 minute (60,000 ms)
            handler.postDelayed(this, 60000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        LogHelper.logd(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogHelper.logd(TAG, "onStartCommand called")
        
        // Ensure foreground is started immediately to comply with Android foreground service requirements
        val initialNotification = createNotification("MosqueDND Active", "Calculating next prayer...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // Start periodic updates
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        try {
            val userManager = getSystemService(Context.USER_SERVICE) as UserManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !userManager.isUserUnlocked) {
                val notification = createNotification("MosqueDND Active", "Monitoring is active (Device Locked)")
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
                return
            }

            val (lat, lng) = LocationProvider.getLastKnownLocation()
            val prefs = MosqueDNDApp.prefs
            val calcMethodStr = prefs.getString("calculation_method", "mwl") ?: "mwl"
            val asrMethodStr = prefs.getString("asr_method", "standard") ?: "standard"
            val calcMethod = CalculationMethod.fromPreferenceValue(calcMethodStr)
            val asrMethod = AsrMethod.fromPreferenceValue(asrMethodStr)
            val offsets = PrayerAlarmPrefs.toOffsetsMap()

            val now = Calendar.getInstance()
            val timesMap = PrayTimesCalculator.calculate(
                date = now.time,
                latitude = lat,
                longitude = lng,
                method = calcMethod,
                asrMethod = asrMethod,
                offsets = offsets
            )

            val prayerOrder = PrayerType.entries.toList()
            val times = prayerOrder.map { timesMap[it]!! }

            var nextIndex = -1
            for (i in times.indices) {
                if (times[i].time.after(now)) {
                    nextIndex = i
                    break
                }
            }

            val nextPrayerTime = if (nextIndex != -1) {
                times[nextIndex]
            } else {
                val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                val tomorrowTimes = PrayTimesCalculator.calculate(
                    date = tomorrow.time,
                    latitude = lat,
                    longitude = lng,
                    method = calcMethod,
                    asrMethod = asrMethod,
                    offsets = offsets
                )
                tomorrowTimes[PrayerType.FAJR]!!
            }

            val diffMs = nextPrayerTime.time.timeInMillis - now.timeInMillis
            val diffSecs = diffMs / 1000
            val hours = diffSecs / 3600
            val minutes = (diffSecs % 3600) / 60

            val timeRemainingStr = if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes}m"
            }

            val prayerName = nextPrayerTime.type.displayName
            val contentText = "Next Prayer: $prayerName in $timeRemainingStr"

            val notification = createNotification("MosqueDND Active", contentText)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Error updating persistent notification", e)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Persistent Status Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live countdown and ensures MosqueDND remains active in background"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        LogHelper.logd(TAG, "Service destroyed")
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }
}