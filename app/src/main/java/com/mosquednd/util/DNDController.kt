package com.mosquednd.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.mosquednd.MosqueDNDApp
import com.mosquednd.R
import com.mosquednd.receiver.DNDTimerExpiryReceiver

object DNDController {

    private const val TAG = "DNDController"

    private val autoDisableTimer by lazy {
        DNDTimer(onTimeout = {
            val ctx = MosqueDNDApp.instance
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                disableDND(ctx)
                Toast.makeText(ctx, R.string.dnd_auto_disabled, Toast.LENGTH_LONG).show()
            }
        })
    }

    fun hasDNDAccess(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    fun requestDNDAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = android.content.Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LogHelper.logd(TAG, "Opening DND access settings")
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun enableDND(context: Context, durationMinutes: Int = 30) {
        if (!hasDNDAccess(context)) {
            LogHelper.logw(TAG, "Cannot enable DND — DND access not granted")
            Toast.makeText(context, "DND access not granted — enable it in Settings", Toast.LENGTH_SHORT).show()
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)

        // Schedule AlarmManager expiry (survives process death)
        scheduleAlarmManagerExpiry(context, durationMinutes)

        // Also start in-process Timer for real-time UI countdown
        autoDisableTimer.reset()

        LogHelper.logd(TAG, "DND enabled (INTERRUPTION_FILTER_NONE) — auto-disable timer started ($durationMinutes min)")
    }

    private fun scheduleAlarmManagerExpiry(context: Context, durationMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + durationMinutes * 60 * 1000L

        // Cancel any previous pending expiry first
        cancelAlarmManagerExpiry(context)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            DNDTimerExpiryReceiver.getPendingIntent(context)
        )
        LogHelper.logd(TAG, "AlarmManager expiry scheduled in ${durationMinutes}min")
    }

    private fun cancelAlarmManagerExpiry(context: Context) {
        DNDTimerExpiryReceiver.cancelPendingIntent(context)
        LogHelper.logd(TAG, "AlarmManager expiry cancelled")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun disableDND(context: Context) {
        if (!hasDNDAccess(context)) {
            LogHelper.logw(TAG, "Cannot disable DND — DND access not granted")
            Toast.makeText(context, "DND access not granted — enable it in Settings", Toast.LENGTH_SHORT).show()
            return
        }

        cancelAlarmManagerExpiry(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        autoDisableTimer.cancel()
        LogHelper.logd(TAG, "DND disabled (INTERRUPTION_FILTER_ALL) — timer cancelled")
    }

    // ── Auto-disable timer (geofence) ──

    fun getTimerRemainingMs(): Long = autoDisableTimer.getRemainingMs()

    fun isTimerActive(): Boolean = autoDisableTimer.isRunning()
}
