package com.mosquednd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mosquednd.R
import com.mosquednd.data.MosqueRepository
import com.mosquednd.MosqueDNDApp
import com.mosquednd.geofence.GeofenceConfig
import com.mosquednd.geofence.GeofenceFilter
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.ui.MainActivity
import com.mosquednd.util.BootPrefs
import com.mosquednd.util.DNDController
import com.mosquednd.util.LogHelper
import com.mosquednd.util.PendingIntentConstants

class LocationMonitorService : android.app.Service() {

    companion object {
        const val CHANNEL_ID = "mosque_monitor_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "start"
        const val EXTRA_INTERVAL = "interval_minutes"
        private const val TAG = "LocationMonitorService"
        private const val PREF_MONITORING_INTERVAL = "pref_monitoring_interval"
        @Volatile
        private var isRunning = false

        fun startService(context: Context, intervalMinutes: Int = 2) {
            val intent = Intent(context, LocationMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INTERVAL, intervalMinutes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            // context.stopService() routes to onDestroy() directly — it does NOT
            // go through onStartCommand(), so no action/extras are needed.
            context.stopService(Intent(context, LocationMonitorService::class.java))
        }

        fun isServiceRunning(): Boolean = isRunning
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        LogHelper.logd(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring(intent)
            null -> LogHelper.logw(TAG, "Service restarted with null intent — not re-initializing (START_NOT_STICKY)")
            else -> LogHelper.logw(TAG, "Unknown action: ${intent?.action}")
        }
        // Note: stopService() calls onDestroy() directly via the Android framework,
        // it does NOT route through onStartCommand(). The stopMonitoring() cleanup
        // is triggered by onDestroy() when the framework stops the service.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring(intent: Intent) {
        if (isRunning) {
            LogHelper.logd(TAG, "Already monitoring, ignoring duplicate start")
            return
        }
        isRunning = true
        var intervalMinutes = intent.getIntExtra(EXTRA_INTERVAL, 2)
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "LOCATION_MONITOR_START", mapOf("interval" to intervalMinutes))

        // Fallback to device-protected prefs if default prefs doesn't have the interval (direct boot)
        if (!MosqueDNDApp.prefs.contains(PREF_MONITORING_INTERVAL)) {
            intervalMinutes = BootPrefs.getMonitoringInterval(this, intervalMinutes)
            LogHelper.logd(TAG, "Falling back to device-protected prefs for interval: ${intervalMinutes}min")
        }

        // Persist monitoring state to both regular and device-protected storage so it
        // survives process death and is available during Direct Boot after reboot.
        MosqueDNDApp.prefs.edit().putInt(PREF_MONITORING_INTERVAL, intervalMinutes).apply()
        BootPrefs.setMonitoringInterval(this, intervalMinutes)
        BootPrefs.setMonitoringEnabled(this, true)
        LogHelper.logd(TAG, "Starting location monitoring (interval=${intervalMinutes}min)")

        val notification = createNotification("Monitoring mosque locations...")
        startForeground(NOTIFICATION_ID, notification)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMinutes * 60 * 1000L
        ).apply {
            setMinUpdateIntervalMillis((intervalMinutes * 60 * 1000L) / 2)
            setMaxUpdateDelayMillis(intervalMinutes * 60 * 1000L * 2)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    LogHelper.logTelemetry(LogHelper.TelemetryCategory.DIAGNOSTIC, "LOCATION_UPDATE_RECEIVED", mapOf("lat" to location.latitude, "lng" to location.longitude))
                    checkIfNearMosque(location)
                } ?: LogHelper.logw(TAG, "LocationResult received but lastLocation was null")
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            LogHelper.logd(TAG, "Location updates requested successfully " +
                    "(interval=${intervalMinutes}min, priority=BALANCED_POWER)")
        } catch (e: SecurityException) {
            LogHelper.loge(TAG, "Missing location permission — cannot monitor", e)
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Failed to request location updates", e)
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopMonitoring() {
        LogHelper.logd(TAG, "Stopping location monitoring")
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "LOCATION_MONITOR_STOP")
        isRunning = false

        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
                LogHelper.logd(TAG, "Location updates removed")
            } catch (e: Exception) {
                LogHelper.loge(TAG, "Error removing location updates", e)
            }
        }
        locationCallback = null

        // Clear device-protected monitoring state so BootReceiver does not restart
        // the service after a reboot the user explicitly stopped monitoring.
        BootPrefs.setMonitoringEnabled(this, false)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun checkIfNearMosque(rawLocation: Location) {
        // Mosque data may be unavailable during Direct Boot (encrypted storage requires
        // unlock). Guard against failure so the foreground service stays alive.
        val savedMosques = try {
            MosqueRepository.getAllMosques()
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Cannot read mosque data (possible Direct Boot) — keeping DND unchanged", e)
            return
        }
        if (savedMosques.isEmpty()) {
            LogHelper.logd(TAG, "No saved mosques — keeping DND unchanged")
            return
        }

        // Reject mock locations before processing — never change DND state based on a spoofed position
        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rawLocation.isMock
        } else {
            @Suppress("DEPRECATION")
            rawLocation.isFromMockProvider
        }
        if (isMock) {
            LogHelper.logw(TAG, "Mock location rejected (provider=${rawLocation.provider}) — keeping DND unchanged")
            return
        }

        // Pass raw location through accuracy gate + moving average smoother.
        // Returns null if accuracy is too poor (GPS jitter rejection).
        val filter = GeofenceFilter.getInstance()
        val filteredLocation = filter.filter(rawLocation)
        if (filteredLocation == null) {
            LogHelper.logw(TAG, "Location sample rejected by accuracy gate — keeping DND unchanged")
            return
        }

        val results = FloatArray(1)
        val nearestMosque = savedMosques.minByOrNull { mosque ->
            Location.distanceBetween(
                filteredLocation.latitude, filteredLocation.longitude,
                mosque.latitude, mosque.longitude,
                results
            )
            results[0]
        }

        Location.distanceBetween(
            filteredLocation.latitude, filteredLocation.longitude,
            nearestMosque!!.latitude, nearestMosque.longitude,
            results
        )
        val distance = results[0]

        val detectionRadius = MosqueDNDApp.prefs.getInt("pref_detection_radius_m", GeofenceConfig.DEFAULT_DETECTION_RADIUS_M).toFloat()
        val isInside = distance <= detectionRadius

        // Require consensus across consecutive filtered samples before acting.
        // This shares state with GeofenceBroadcastReceiver via the singleton,
        // so both paths contribute to the same per-fence tracking.
        val consensusReached = filter.checkConsensus(nearestMosque.id, isInside)

        if (isInside) {
            LogHelper.logd(TAG, "Near mosque [REDACTED] ([REDACTED]m) — " +
                    "consensus=$consensusReached")

            if (consensusReached) {
                if (PrayerAlarmPrefs.isAutoDNDEnabled()) {
                    val durationMinutes = PrayerAlarmPrefs.getAutoDNDDurationMin()
                    DNDController.enableDND(this, durationMinutes)
                    LogHelper.logd(TAG, "Consensus reached — auto-DND enabled")
                } else {
                    LogHelper.logw(TAG, "Auto-DND is disabled in settings — skipping DND enable")
                }
                updateNotification("Near mosque: ${nearestMosque.name}")
            } else {
                LogHelper.logd(TAG, "Near mosque but consensus not yet reached — DND unchanged")
            }
        } else {
            LogHelper.logd(TAG, "Not near any saved mosque (closest [REDACTED]m) — " +
                    "consensus=$consensusReached")

            if (consensusReached) {
                DNDController.disableDND(this)
                LogHelper.logd(TAG, "Consensus reached — DND disabled")
            } else {
                LogHelper.logd(TAG, "Not near mosque but consensus not yet reached — DND unchanged")
            }
            updateNotification("Monitoring mosque locations...")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mosque Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors location for mosques"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            LogHelper.logd(TAG, "Notification channel created")
        }
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, PendingIntentConstants.NOTIFICATION_SERVICE_TAP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Open App",
            pendingIntent
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mosque DND Monitor")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(openAppAction)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
    }

    override fun onDestroy() {
        LogHelper.logd(TAG, "Service destroyed")
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
                LogHelper.loge(TAG, "Error during onDestroy cleanup", e)
            }
        }
        isRunning = false
        super.onDestroy()
    }
}
