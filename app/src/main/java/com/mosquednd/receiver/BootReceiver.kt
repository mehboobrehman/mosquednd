package com.mosquednd.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mosquednd.MosqueDNDApp
import com.mosquednd.data.MosqueRepository
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.service.GeofenceService
import com.mosquednd.service.LocationMonitorService
import com.mosquednd.util.BootPrefs
import com.mosquednd.util.EncryptedPrefsManager
import com.mosquednd.util.LocationProvider

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            Log.w(TAG, "Received null intent in BootReceiver")
            return
        }

        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                // Direct Boot — device is booted but not yet unlocked.
                // Only device-protected storage is available; skip credential-protected
                // operations (prayer recalculation, geofence re-registration) which
                // will run once BOOT_COMPLETED fires after unlock.
                Log.d(TAG, "LOCKED_BOOT_COMPLETED received — checking device-protected monitoring state")
                restartMonitoringIfEnabled(context, syncToDefaultPrefs = false)
                com.mosquednd.service.PersistentMonitoringService.startService(context)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Boot completed or package replaced (action=${intent.action}) — full initialization")
                // Ensure encrypted-backed data is available. The process may have started
                // during Direct Boot when EncryptedSharedPreferences could not initialise.
                try {
                    MosqueRepository.init(
                        encryptedPrefs = EncryptedPrefsManager.getEncryptedPrefs(context, "mosque_repo"),
                        context = context
                    )
                    PrayerAlarmPrefs.init(EncryptedPrefsManager.getEncryptedPrefs(context, "alarm_prefs"))
                    LocationProvider.initEncryptedPrefs(EncryptedPrefsManager.getEncryptedPrefs(context, "location_prefs"))
                } catch (e: Exception) {
                    Log.w(TAG, "Encrypted prefs re-init failed on BOOT_COMPLETED", e)
                }
                DailyRecalculationHelper.recalculateAndReschedule(context)
                GeofenceService.enqueueReRegisterAll(context)
                restartMonitoringIfEnabled(context, syncToDefaultPrefs = true)
                com.mosquednd.service.PersistentMonitoringService.startService(context)
            }

            else -> {
                Log.w(TAG, "Ignoring unexpected broadcast action: ${intent.action}")
            }
        }
    }

    /**
     * Reads the monitoring state from device-protected prefs and restarts
     * LocationMonitorService if monitoring was active before reboot.
     *
     * @param syncToDefaultPrefs when true (post-unlock), mirrors the device-protected
     *   values into the default credential-protected prefs so the rest of the app
     *   sees a consistent state.
     */
    private fun restartMonitoringIfEnabled(context: Context, syncToDefaultPrefs: Boolean) {
        if (!BootPrefs.isMonitoringEnabled(context)) {
            Log.d(TAG, "Monitoring was not active before boot — nothing to restart")
            return
        }

        val savedInterval = BootPrefs.getMonitoringInterval(context)
        Log.d(TAG, "Monitoring was active before boot — restarting LocationMonitorService (interval=${savedInterval}min)")
        LocationMonitorService.startService(context, savedInterval)

        if (syncToDefaultPrefs) {
            MosqueDNDApp.prefs.edit()
                .putBoolean(BootPrefs.KEY_MONITORING_ENABLED, true)
                .putInt(BootPrefs.KEY_MONITORING_INTERVAL, savedInterval)
                .apply()
        }
    }
}
