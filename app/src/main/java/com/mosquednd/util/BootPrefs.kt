package com.mosquednd.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Provides access to device-protected SharedPreferences for monitoring state.
 *
 * Device-protected storage is available during Direct Boot (before first unlock),
 * unlike the default credential-protected SharedPreferences. Only monitoring-related
 * keys live here; all other settings remain in the regular default prefs.
 */
object BootPrefs {

    private const val TAG = "BootPrefs"
    private const val PREFS_NAME = "boot_prefs"

    const val KEY_MONITORING_ENABLED = "pref_monitoring_enabled"
    const val KEY_MONITORING_INTERVAL = "pref_monitoring_interval"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isMonitoringEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_MONITORING_ENABLED, false)

    fun getMonitoringInterval(context: Context, default: Int = 30): Int =
        getPrefs(context).getInt(KEY_MONITORING_INTERVAL, default)

    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_MONITORING_ENABLED, enabled)
            .apply()
        Log.d(TAG, "Monitoring enabled persisted to device-protected storage: $enabled")
    }

    fun setMonitoringInterval(context: Context, intervalMinutes: Int) {
        getPrefs(context).edit()
            .putInt(KEY_MONITORING_INTERVAL, intervalMinutes)
            .apply()
        Log.d(TAG, "Monitoring interval persisted to device-protected storage: ${intervalMinutes}min")
    }

    /**
     * Copies monitoring state from the default (credential-protected) SharedPreferences
     * into device-protected storage, but only if it hasn't been written here yet.
     * Called once on app launch to bridge pre-update state into Direct Boot storage.
     */
    fun migrateFromDefaultPrefs(context: Context, defaultPrefs: SharedPreferences) {
        val bootPrefs = getPrefs(context)
        if (bootPrefs.contains(KEY_MONITORING_ENABLED)) {
            return
        }
        val enabled = defaultPrefs.getBoolean(KEY_MONITORING_ENABLED, false)
        val interval = defaultPrefs.getInt(KEY_MONITORING_INTERVAL, 30)
        bootPrefs.edit()
            .putBoolean(KEY_MONITORING_ENABLED, enabled)
            .putInt(KEY_MONITORING_INTERVAL, interval)
            .apply()
        Log.d(TAG, "Migrated monitoring state to device-protected storage (enabled=$enabled, interval=${interval}min)")
    }
}
