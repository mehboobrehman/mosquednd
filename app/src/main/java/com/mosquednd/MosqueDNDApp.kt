package com.mosquednd

import android.app.Application
import android.os.UserManager
import android.util.Log
import androidx.preference.PreferenceManager
import com.mapbox.mapboxsdk.Mapbox
import com.mosquednd.data.MosqueRepository
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.util.BootPrefs
import com.mosquednd.util.EncryptedPrefsManager
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.AgentLogger

class MosqueDNDApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize AgentLogger FIRST — AgentLogger.d() calls below trigger
        // subnet discovery which needs appContext (set by init). If init() is
        // called after the d() calls, appContext is null during discovery and
        // the WifiManager subnet detection fails, causing the wrong subnet to
        // be scanned and the server never being found.
        AgentLogger.init(this)

        val versionInfo = AppVersionInfo.getCurrent()
        Log.i(TAG, "=== APP INITIALIZATION ===")
        Log.i(TAG, "App Version: ${versionInfo.versionName} (Code: ${versionInfo.versionCode})")
        Log.i(TAG, "Git Commit Hash: ${versionInfo.gitCommitHash}")
        Log.i(TAG, "Build Timestamp: ${versionInfo.buildTimestamp}")
        Log.i(TAG, "Environment: ${versionInfo.environment}")
        AgentLogger.i("App Version: ${versionInfo.versionName}, Hash: ${versionInfo.gitCommitHash}, Env: ${versionInfo.environment}, Build: ${versionInfo.buildTimestamp}")

        Mapbox.getInstance(this)
        AgentLogger.d("Mapbox SDK initialized.")
        instance = this
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        LocationProvider.init(this)
        AgentLogger.d("LocationProvider initialized.")
        AgentLogger.i("MosqueDNDApp started. AgentLogger initialized.")

        // EncryptedSharedPreferences requires the Android Keystore, which is not available
        // during Direct Boot (before first unlock). Guard so the process survives and the
        // monitoring service can restart from device-protected prefs.
        try {
            val mosqueRepoPrefs = EncryptedPrefsManager.getEncryptedPrefs(this, "mosque_repo")
            val alarmPrefs = EncryptedPrefsManager.getEncryptedPrefs(this, "alarm_prefs")
            val locationPrefs = EncryptedPrefsManager.getEncryptedPrefs(this, "location_prefs")

            // One-time data migration: copy sensitive keys from the default SharedPreferences
            // (where all data lived before the encrypted migration) to the corresponding
            // encrypted files. Without this, existing users lose all data on upgrade.
            migrateSensitivePrefsIfNeeded(mosqueRepoPrefs, alarmPrefs, locationPrefs)
            migrateAutoDndPrefsIfNeeded(alarmPrefs)

            MosqueRepository.init(
                encryptedPrefs = mosqueRepoPrefs,
                context = this
            )
            PrayerAlarmPrefs.init(alarmPrefs)
            LocationProvider.initEncryptedPrefs(locationPrefs)
            AgentLogger.d("EncryptedPrefs initialized: mosqueRepo, alarm, location.")
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted prefs init deferred (possible Direct Boot mode)", e)
            AgentLogger.w("Encrypted prefs init deferred: ${e.message}")
        }

        // One-time migration of monitoring state into device-protected storage so that
        // BootReceiver can read it during Direct Boot after reboot.
        val userManager = getSystemService(UserManager::class.java)
        if (userManager == null || userManager.isUserUnlocked) {
            BootPrefs.migrateFromDefaultPrefs(this, prefs)
        }

        // Start persistent monitoring service to ensure MosqueDND is protected from being killed
        com.mosquednd.service.PersistentMonitoringService.startService(this)
        AgentLogger.d("PersistentMonitoringService started.")
        com.mosquednd.service.ServiceWatchdogWorker.schedule(this)
        AgentLogger.d("ServiceWatchdogWorker scheduled.")
        AgentLogger.i("MosqueDNDApp onCreate complete.")
    }

    /**
     * One-time migration of sensitive keys from the default SharedPreferences (where all
     * data lived before the encrypted migration) to the corresponding EncryptedSharedPreferences
     * files. Each key is classified by prefix/pattern and copied to the correct target.
     *
     * Protected by a sentinel key (`encrypted_migration_complete`) so it only runs once.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    private fun migrateSensitivePrefsIfNeeded(
        mosqueRepoPrefs: android.content.SharedPreferences,
        alarmPrefs: android.content.SharedPreferences,
        locationPrefs: android.content.SharedPreferences
    ) {
        if (prefs.getBoolean(PREFS_MIGRATION_COMPLETE, false)) return

        Log.d(TAG, "Starting one-time migration of sensitive prefs to encrypted storage")

        var migratedCount = 0
        val defaultEditor = prefs.edit()
        val mosqueEditor = mosqueRepoPrefs.edit()
        val alarmEditor = alarmPrefs.edit()
        val locationEditor = locationPrefs.edit()

        for (key in prefs.all.keys) {
            when {
                // Mosque repository
                key == KEY_MOSQUES_JSON -> {
                    val value = prefs.all[key]
                    if (value is String) {
                        mosqueEditor.putString(key, value)
                        defaultEditor.remove(key)
                        migratedCount++
                    }
                }

                // Location provider
                key in LOCATION_KEYS -> {
                    val value = prefs.all[key] ?: continue
                    when (value) {
                        is Float -> locationEditor.putFloat(key, value)
                        is Boolean -> locationEditor.putBoolean(key, value)
                        is Int -> locationEditor.putInt(key, value)
                        is String -> locationEditor.putString(key, value)
                    }
                    defaultEditor.remove(key)
                    migratedCount++
                }

                // Prayer alarm prefs (offsets, alarm toggles, master alarm, custom Adhan URI,
                // adhan mute flags, hijri date offset, DND settings)
                key.startsWith("pref_prayer_") ||
                key.startsWith("pref_alarm_enabled_") ||
                key == PREFS_MASTER_ALARM ||
                key == PREFS_CUSTOM_ADHAN_URI ||
                key.startsWith("pref_adhan_muted_") ||
                key == PREFS_HIJRI_DATE_OFFSET ||
                key.startsWith("pref_dnd_enabled_") ||
                key.startsWith("pref_dnd_offset_") ||
                key.startsWith("pref_dnd_duration_") -> {
                    val value = prefs.all[key] ?: continue
                    when (value) {
                        is Int -> alarmEditor.putInt(key, value)
                        is Boolean -> alarmEditor.putBoolean(key, value)
                        is String -> alarmEditor.putString(key, value)
                    }
                    defaultEditor.remove(key)
                    migratedCount++
                }
            }
        }

        // Apply all batched writes to encrypted prefs, then commit removals + sentinel
        // to default prefs in one atomic write
        mosqueEditor.apply()
        alarmEditor.apply()
        locationEditor.apply()
        defaultEditor.putBoolean(PREFS_MIGRATION_COMPLETE, true).apply()
        Log.d(TAG, "Migration complete — $migratedCount keys copied to encrypted storage")
    }

    /**
     * One-time migration of the global auto-DND toggle and duration from the default
     * SharedPreferences (where PreferenceFragmentCompat stored them) into the encrypted
     * alarm store. Runs independently of [migrateSensitivePrefsIfNeeded] so it also fires
     * for users who already completed the broader encrypted migration. Removing the keys
     * from the default prefs ensures backup rules (file-level) never include them.
     */
    private fun migrateAutoDndPrefsIfNeeded(alarmPrefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(PREFS_AUTO_DND_MIGRATION_COMPLETE, false)) return

        val alarmEditor = alarmPrefs.edit()
        val defaultEditor = prefs.edit()
        var migrated = false

        if (prefs.contains(KEY_PREF_AUTO_DND_ENABLED)) {
            alarmEditor.putBoolean(
                KEY_PREF_AUTO_DND_ENABLED,
                prefs.getBoolean(KEY_PREF_AUTO_DND_ENABLED, true)
            )
            defaultEditor.remove(KEY_PREF_AUTO_DND_ENABLED)
            migrated = true
        }
        if (prefs.contains(KEY_PREF_AUTO_DND_DURATION_MIN)) {
            alarmEditor.putString(
                KEY_PREF_AUTO_DND_DURATION_MIN,
                prefs.getString(KEY_PREF_AUTO_DND_DURATION_MIN, "30")
            )
            defaultEditor.remove(KEY_PREF_AUTO_DND_DURATION_MIN)
            migrated = true
        }

        alarmEditor.apply()
        defaultEditor.putBoolean(PREFS_AUTO_DND_MIGRATION_COMPLETE, true).apply()
        if (migrated) {
            Log.d(TAG, "Auto-DND prefs migrated to encrypted storage")
        }
    }

    companion object {
        private const val TAG = "MosqueDNDApp"
        lateinit var instance: MosqueDNDApp
            private set
        lateinit var prefs: android.content.SharedPreferences
            private set

        const val PREFS_THEME = "theme_preference"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"

        // Sect selector constants
        const val PREFS_SECT = "pref_sect"

        // Migration sentinel key (stored in default prefs, not encrypted)
        private const val PREFS_MIGRATION_COMPLETE = "encrypted_migration_complete"
        // Sentinel for the independent auto-DND migration (see migrateAutoDndPrefsIfNeeded)
        private const val PREFS_AUTO_DND_MIGRATION_COMPLETE = "auto_dnd_migration_complete"

        // Keys classified for migration
        private const val KEY_MOSQUES_JSON = "mosques_json"
        private const val PREFS_MASTER_ALARM = "pref_master_alarm_enabled"
        private const val PREFS_CUSTOM_ADHAN_URI = "pref_custom_adhan_uri"
        private const val PREFS_HIJRI_DATE_OFFSET = "hijri_date_offset"
        private const val KEY_PREF_AUTO_DND_ENABLED = "pref_auto_dnd_enabled"
        private const val KEY_PREF_AUTO_DND_DURATION_MIN = "pref_auto_dnd_duration_min"

        private val LOCATION_KEYS = setOf(
            "last_known_lat",
            "last_known_lng",
            "has_cached_location",
            "location_version",
            "location_override_lat",
            "location_override_lng"
        )
    }
}
