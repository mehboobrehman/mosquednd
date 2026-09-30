package com.mosquednd.data

/**
 * Version 1 export schema for MosqueDND data.
 *
 * Serialized to/from JSON via Gson for the Import/Export feature.
 * All fields use String/primitive types for clean Gson serialization.
 */
data class ExportData(
    /** Schema version for forward compatibility. Must be 1 for this version. */
    val exportVersion: Int = 1,

    /** ISO 8601 timestamp of when the export was created. */
    val exportedAt: String = "",

    /** Calculation method preference value (e.g. "mwl", "isna", "karachi"). */
    val calculationMethod: String = "mwl",

    /** Asr method preference value (e.g. "standard", "hanafi"). */
    val asrMethod: String = "standard",

    /** High latitude method preference value (e.g. "none", "angle_based"). */
    val highLatitudeMethod: String = "none",

    /** Whether the master alarm toggle is enabled. */
    val masterAlarmEnabled: Boolean = true,

    /** Per-prayer offset in minutes (PrayerType.name.lowercase() -> minutes). Nullable because Gson may set null via reflection. */
    val prayerOffsets: Map<String, Int>? = null,

    /** Per-prayer alarm enabled state (PrayerType.name.lowercase() -> enabled). Nullable because Gson may set null via reflection. */
    val prayerAlarms: Map<String, Boolean>? = null,

    /** Custom Adhan ringtone URI, or null for the default. */
    val customAdhanUri: String? = null,

    /** List of saved mosque locations. */
    val savedMosques: List<Mosque> = emptyList(),

    /** Theme preference (e.g. "system", "light", "dark"). */
    val themePreference: String = "system",

    /** Per-prayer DND enabled state (PrayerType.name.lowercase() -> enabled). Nullable because Gson may set null via reflection. */
    val dndEnabled: Map<String, Boolean>? = null,

    /** Per-prayer DND offset in minutes (PrayerType.name.lowercase() -> minutes). Nullable because Gson may set null via reflection. */
    val dndOffsets: Map<String, Int>? = null,

    /** Per-prayer DND duration in minutes (PrayerType.name.lowercase() -> minutes). Nullable because Gson may set null via reflection. */
    val dndDurations: Map<String, Int>? = null
)
