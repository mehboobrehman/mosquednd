package com.mosquednd.prayer

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.mosquednd.MosqueDNDApp
import com.mosquednd.util.LogHelper

/**
 * Singleton that centralizes SharedPreferences access for per-prayer alarm settings,
 * offsets, master toggle, and custom ringtone URI.
 *
 * All storage uses MosqueDNDApp.prefs (existing SharedPreferences singleton).
 * Offset values are clamped to -60..+60 minutes.
 */
object PrayerAlarmPrefs {

    private const val TAG = "PrayerAlarmPrefs"

    // Key constants
    private const val PREFS_OFFSET_PREFIX = "pref_prayer_"
    private const val PREFS_OFFSET_SUFFIX = "_offset"
    private const val PREFS_ALARM_ENABLED_PREFIX = "pref_alarm_enabled_"
    private const val PREFS_MASTER_ALARM = "pref_master_alarm_enabled"
    private const val PREFS_CUSTOM_ADHAN_URI = "pref_custom_adhan_uri"
    private const val PREFS_ADHAN_MUTED_PREFIX = "pref_adhan_muted_"
    private const val PREFS_HIJRI_DATE_OFFSET = "hijri_date_offset"

    // DND preference key constants
    private const val PREFS_DND_ENABLED_PREFIX = "pref_dnd_enabled_"
    private const val PREFS_DND_OFFSET_PREFIX = "pref_dnd_offset_"
    private const val PREFS_DND_DURATION_PREFIX = "pref_dnd_duration_"

    // Global auto-DND (geofence / location-monitor triggered) preference keys.
    // Migrated from the default SharedPreferences into this encrypted store so
    // that backup rules (file-level) keep them out of cloud/device backups.
    private const val PREFS_AUTO_DND_ENABLED = "pref_auto_dnd_enabled"
    private const val PREFS_AUTO_DND_DURATION_MIN = "pref_auto_dnd_duration_min"
    private const val DEFAULT_AUTO_DND_ENABLED = true
    private const val DEFAULT_AUTO_DND_DURATION_MIN = 30

    private const val MIN_OFFSET = -60
    private const val MAX_OFFSET = 60

    // Hijri date offset range constants
    private const val MIN_HIJRI_OFFSET = -2
    private const val MAX_HIJRI_OFFSET = 2

    // DND-specific constants
    private const val MIN_DND_OFFSET = -60
    private const val MAX_DND_OFFSET = 60
    private val VALID_DND_DURATIONS = setOf(5, 10, 15, 20, 30, 60)
    private const val DEFAULT_DND_DURATION = 10

    @VisibleForTesting
    internal var testPrefs: SharedPreferences? = null

    private var encryptedPrefs: SharedPreferences? = null

    /**
     * Initialize with encrypted SharedPreferences for at-rest encryption of sensitive
     * alarm/offset/DND/custom Adhan data. Called from MosqueDNDApp.onCreate().
     * Falls back to regular MosqueDNDApp.prefs if not initialized (e.g. during Direct Boot).
     */
    fun init(encryptedPrefs: SharedPreferences) {
        this.encryptedPrefs = encryptedPrefs
        logDebug("Initialized with encrypted SharedPreferences")
    }

    /**
     * Initialize with a test SharedPreferences instance. For unit testing only.
     */
    @VisibleForTesting
    fun initForTest(prefs: SharedPreferences) {
        testPrefs = prefs
    }

    private val prefs get() = testPrefs ?: encryptedPrefs ?: MosqueDNDApp.prefs

    fun getOffset(type: PrayerType, default: Int = 0): Int {
        val key = offsetKey(type)
        val stored = prefs.getInt(key, default)
        return stored.coerceIn(MIN_OFFSET, MAX_OFFSET)
    }

    fun setOffset(type: PrayerType, minutes: Int) {
        val clamped = minutes.coerceIn(MIN_OFFSET, MAX_OFFSET)
        if (clamped != minutes) {
            logWarn("Offset for prayer clamped")
        }
        prefs.edit().putInt(offsetKey(type), clamped).apply()
        logDebug("Offset for prayer set")
    }

    fun isAlarmEnabled(type: PrayerType, default: Boolean = true): Boolean {
        return prefs.getBoolean(alarmKey(type), default)
    }

    fun setAlarmEnabled(type: PrayerType, enabled: Boolean) {
        prefs.edit().putBoolean(alarmKey(type), enabled).apply()
        logDebug("Alarm [REDACTED] ${if (enabled) "enabled" else "disabled"}")
    }

    fun toOffsetsMap(): Map<PrayerType, Int> {
        return PrayerType.entries.associate { type ->
            type to getOffset(type)
        }
    }

    fun getEnabledPrayerTypes(): List<PrayerType> {
        return PrayerType.entries.filter { type ->
            type != PrayerType.SUNRISE && isAlarmEnabled(type)
        }
    }

    fun isMasterAlarmEnabled(): Boolean {
        return prefs.getBoolean(PREFS_MASTER_ALARM, true)
    }

    fun setMasterAlarmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREFS_MASTER_ALARM, enabled).apply()
        logDebug("Master alarm ${if (enabled) "enabled" else "disabled"}")
    }

    // ──────────────────────────────────────────────
    // Per-prayer adhan mute flag
    // ──────────────────────────────────────────────

    fun isAdhanMuted(type: PrayerType): Boolean {
        return prefs.getBoolean(adhanMutedKey(type), false)
    }

    fun setAdhanMuted(type: PrayerType, muted: Boolean) {
        prefs.edit().putBoolean(adhanMutedKey(type), muted).apply()
        logDebug("Adhan [REDACTED] ${if (muted) "muted" else "unmuted"}")
    }

    fun getCustomAdhanUri(): String? {
        val uri = prefs.getString(PREFS_CUSTOM_ADHAN_URI, null)
        return if (uri.isNullOrBlank()) null else uri
    }

    fun setCustomAdhanUri(uri: String?) {
        prefs.edit().putString(PREFS_CUSTOM_ADHAN_URI, uri).apply()
        logDebug("Custom adhan URI ${if (uri != null) "set" else "cleared"}")
    }

    fun isOffsetKey(key: String?): Boolean {
        return key != null && key.startsWith(PREFS_OFFSET_PREFIX) && key.endsWith(PREFS_OFFSET_SUFFIX)
    }

    fun isAlarmEnabledKey(key: String?): Boolean {
        return key != null && key.startsWith(PREFS_ALARM_ENABLED_PREFIX)
    }

    // ──────────────────────────────────────────────
    // DND Enabled State
    // ──────────────────────────────────────────────

    fun isDNDEnabled(type: PrayerType, default: Boolean = false): Boolean {
        return prefs.getBoolean(dndEnabledKey(type), default)
    }

    fun setDNDEnabled(type: PrayerType, enabled: Boolean) {
        prefs.edit().putBoolean(dndEnabledKey(type), enabled).apply()
        logDebug("DND [REDACTED] ${if (enabled) "enabled" else "disabled"}")
    }

    // ──────────────────────────────────────────────
    // DND Offset (-60..+60 minutes)
    // ──────────────────────────────────────────────

    fun getDNDOffset(type: PrayerType, default: Int = 0): Int {
        val key = dndOffsetKey(type)
        val stored = prefs.getInt(key, default)
        return stored.coerceIn(MIN_DND_OFFSET, MAX_DND_OFFSET)
    }

    fun setDNDOffset(type: PrayerType, minutes: Int) {
        val clamped = minutes.coerceIn(MIN_DND_OFFSET, MAX_DND_OFFSET)
        if (clamped != minutes) {
            logWarn("DND offset for prayer clamped")
        }
        prefs.edit().putInt(dndOffsetKey(type), clamped).apply()
        logDebug("DND offset for prayer set")
    }

    // ──────────────────────────────────────────────
    // Hijri Date Offset (-2..+2 days)
    // ──────────────────────────────────────────────

    fun getHijriDateOffset(default: Int = 0): Int {
        val storedStr = prefs.getString(PREFS_HIJRI_DATE_OFFSET, default.toString())
        val stored = storedStr?.toIntOrNull() ?: default
        val clamped = stored.coerceIn(MIN_HIJRI_OFFSET, MAX_HIJRI_OFFSET)
        if (clamped != stored) {
            logWarn("Hijri date offset $stored out of range, clamped to $clamped")
        }
        return clamped
    }

    // ──────────────────────────────────────────────
    // DND Duration (clamped to nearest valid value)
    // ──────────────────────────────────────────────

    fun getDNDDuration(type: PrayerType, default: Int = DEFAULT_DND_DURATION): Int {
        return prefs.getInt(dndDurationKey(type), default)
    }

    fun setDNDDuration(type: PrayerType, minutes: Int) {
        val clamped = clampDuration(minutes)
        if (clamped != minutes) {
            logWarn("DND duration for prayer clamped")
        }
        prefs.edit().putInt(dndDurationKey(type), clamped).apply()
        logDebug("DND duration for prayer set")
    }

    fun setDNDDurationRaw(type: PrayerType, minutes: Int) {
        val clamped = minutes.coerceIn(1, 999)
        if (clamped != minutes) {
            logWarn("DND duration raw for prayer clamped")
        }
        prefs.edit().putInt(dndDurationKey(type), clamped).apply()
        logDebug("DND duration (raw) for prayer set")
    }

    fun isDNDDurationCustom(type: PrayerType): Boolean {
        val stored = prefs.getInt(dndDurationKey(type), DEFAULT_DND_DURATION)
        return stored !in VALID_DND_DURATIONS
    }

    // ──────────────────────────────────────────────
    // Global Auto-DND (geofence / location-monitor triggered DND toggle + duration)
    // ──────────────────────────────────────────────

    fun isAutoDNDEnabled(default: Boolean = DEFAULT_AUTO_DND_ENABLED): Boolean {
        return prefs.getBoolean(PREFS_AUTO_DND_ENABLED, default)
    }

    fun setAutoDNDEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREFS_AUTO_DND_ENABLED, enabled).apply()
        logDebug("Auto-DND ${if (enabled) "enabled" else "disabled"}")
    }

    fun getAutoDNDDurationMin(default: Int = DEFAULT_AUTO_DND_DURATION_MIN): Int {
        val storedStr = prefs.getString(PREFS_AUTO_DND_DURATION_MIN, default.toString())
        return storedStr?.toIntOrNull() ?: default
    }

    fun setAutoDNDDurationMin(minutes: Int) {
        prefs.edit().putString(PREFS_AUTO_DND_DURATION_MIN, minutes.toString()).apply()
        logDebug("Auto-DND duration set to $minutes min")
    }

    // ──────────────────────────────────────────────
    // Bulk-read Maps (for export / iteration)
    // ──────────────────────────────────────────────

    fun toDNDEnabledMap(): Map<PrayerType, Boolean> {
        return PrayerType.entries.associate { type ->
            type to isDNDEnabled(type)
        }
    }

    fun toDNDOffsetsMap(): Map<PrayerType, Int> {
        return PrayerType.entries.associate { type ->
            type to getDNDOffset(type)
        }
    }

    fun toDNDDurationsMap(): Map<PrayerType, Int> {
        return PrayerType.entries.associate { type ->
            type to getDNDDuration(type)
        }
    }

    // ──────────────────────────────────────────────
    // Key Check Helpers (for SharedPreference listeners)
    // ──────────────────────────────────────────────

    fun isDNDEnabledKey(key: String?): Boolean {
        return key != null && key.startsWith(PREFS_DND_ENABLED_PREFIX)
    }

    fun isDNDOffsetKey(key: String?): Boolean {
        return key != null && key.startsWith(PREFS_DND_OFFSET_PREFIX)
    }

    fun isDNDDurationKey(key: String?): Boolean {
        return key != null && key.startsWith(PREFS_DND_DURATION_PREFIX)
    }

    private fun logDebug(msg: String) {
        try {
            LogHelper.logd(TAG, msg)
        } catch (_: RuntimeException) {
            // android.util.Log stub throws on JVM tests
        }
    }

    private fun logWarn(msg: String) {
        try {
            LogHelper.logw(TAG, msg)
        } catch (_: RuntimeException) {
            // android.util.Log stub throws on JVM tests
        }
    }

    private fun clampDuration(minutes: Int): Int {
        if (minutes in VALID_DND_DURATIONS) return minutes
        return when {
            minutes <= 7 -> 5
            minutes <= 12 -> 10
            minutes <= 17 -> 15
            minutes <= 25 -> 20
            minutes <= 45 -> 30
            else -> 60
        }
    }

    private fun offsetKey(type: PrayerType): String =
        "${PREFS_OFFSET_PREFIX}${type.name.lowercase()}${PREFS_OFFSET_SUFFIX}"

    private fun alarmKey(type: PrayerType): String =
        "${PREFS_ALARM_ENABLED_PREFIX}${type.name.lowercase()}"

    private fun dndEnabledKey(type: PrayerType): String =
        "${PREFS_DND_ENABLED_PREFIX}${type.name.lowercase()}"

    private fun dndOffsetKey(type: PrayerType): String =
        "${PREFS_DND_OFFSET_PREFIX}${type.name.lowercase()}"

    private fun dndDurationKey(type: PrayerType): String =
        "${PREFS_DND_DURATION_PREFIX}${type.name.lowercase()}"

    private fun adhanMutedKey(type: PrayerType): String =
        "${PREFS_ADHAN_MUTED_PREFIX}${type.name.lowercase()}"
}