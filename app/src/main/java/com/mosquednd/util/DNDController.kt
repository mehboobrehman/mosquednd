package com.mosquednd.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.mosquednd.MosqueDNDApp
import com.mosquednd.R
import com.mosquednd.data.DNDLogRepository
import com.mosquednd.receiver.DNDTimerExpiryReceiver

object DNDController {

    private const val TAG = "DNDController"
    private const val PREFS_NAME = "dnd_controller_prefs"
    private const val KEY_SAVED_FILTER = "saved_interruption_filter"
    private const val KEY_APP_OWNED_DND = "app_owned_dnd"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun saveDndState(context: Context, filter: Int) {
        getPrefs(context).edit()
            .putInt(KEY_SAVED_FILTER, filter)
            .putBoolean(KEY_APP_OWNED_DND, true)
            .apply()
        LogHelper.logd(TAG, "Saved original DND filter ($filter) and marked as app-owned")
    }

    private fun getSavedDndState(context: Context): Int {
        val filter = getPrefs(context).getInt(KEY_SAVED_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        LogHelper.logd(TAG, "Retrieved saved DND filter: $filter")
        return filter
    }

    private fun clearDndState(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_SAVED_FILTER)
            .putBoolean(KEY_APP_OWNED_DND, false)
            .apply()
        LogHelper.logd(TAG, "Cleared saved DND state and app-owned flag")
    }

    private fun isAppOwnedDnd(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_APP_OWNED_DND, false)

    private val autoDisableTimer by lazy {
        DNDTimer(onTimeout = {
            val ctx = MosqueDNDApp.instance
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                disableDND(ctx, "Auto-disabled after timer expiry")
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
    fun enableDND(context: Context, durationMinutes: Int = 30, mosqueName: String? = null) {
        if (!hasDNDAccess(context)) {
            LogHelper.logw(TAG, "Cannot enable DND — DND access not granted")
            Toast.makeText(context, "DND access not granted — enable it in Settings", Toast.LENGTH_SHORT).show()
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // H2: Read and store the current interruption filter state if not already owned by app
        if (!isAppOwnedDnd(context)) {
            val currentFilter = notificationManager.currentInterruptionFilter
            saveDndState(context, currentFilter)
        }

        // H1: Change the default interruption filter from INTERRUPTION_FILTER_NONE to INTERRUPTION_FILTER_ALARMS
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)

        // Schedule AlarmManager expiry (survives process death)
        scheduleAlarmManagerExpiry(context, durationMinutes)

        // Also start in-process Timer for real-time UI countdown
        autoDisableTimer.reset()

        LogHelper.logd(TAG, "DND enabled (INTERRUPTION_FILTER_ALARMS) — auto-disable timer started ($durationMinutes min)")

        // Log DND activation
        val desc = if (mosqueName != null) {
            "AutoDND enabled for $mosqueName ($durationMinutes min)"
        } else {
            "AutoDND enabled ($durationMinutes min)"
        }
        DNDLogRepository.insertLog(context, "DND_ENABLED", desc, mosqueName)
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
    fun disableDND(context: Context, reason: String = "Manual disable") {
        if (!hasDNDAccess(context)) {
            LogHelper.logw(TAG, "Cannot disable DND — DND access not granted")
            Toast.makeText(context, "DND access not granted — enable it in Settings", Toast.LENGTH_SHORT).show()
            return
        }

        cancelAlarmManagerExpiry(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // H2: Restore the previously saved interruption filter instead of hardcoding INTERRUPTION_FILTER_ALL
        if (isAppOwnedDnd(context)) {
            val savedFilter = getSavedDndState(context)
            notificationManager.setInterruptionFilter(savedFilter)
            clearDndState(context)
            LogHelper.logd(TAG, "DND disabled (restored saved filter: $savedFilter) — timer cancelled")
        } else {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            LogHelper.logd(TAG, "DND disabled (forced INTERRUPTION_FILTER_ALL, no saved state) — timer cancelled")
        }

        autoDisableTimer.cancel()

        // Log DND deactivation
        DNDLogRepository.insertLog(context, "DND_DISABLED", reason)
    }

    // ── Auto-disable timer (geofence) ──

    fun getTimerRemainingMs(): Long = autoDisableTimer.getRemainingMs()

    fun isTimerActive(): Boolean = autoDisableTimer.isRunning()
}
