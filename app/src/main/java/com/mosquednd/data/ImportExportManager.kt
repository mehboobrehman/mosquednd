package com.mosquednd.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.mosquednd.util.LogHelper
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.mosquednd.MosqueDNDApp
import com.mosquednd.prayer.AsrMethod
import com.mosquednd.prayer.CalculationMethod
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerType
import com.mosquednd.theme.AppTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton manager for exporting and importing MosqueDND user data.
 *
 * Export reads all current state (prayer settings, alarms, offsets, mosques, theme)
 * and serializes to a JSON file shared via ACTION_SEND.
 *
 * Import reads a JSON file via SAF, validates the schema, shows a preview dialog,
 * and merges the data into the current app state.
 */
object ImportExportManager {

    private const val TAG = "ImportExportManager"
    private const val EXPORT_VERSION = 1
    private const val EXPORT_DIR = "export"
    private const val EXPORT_FILENAME = "mosquednd_data.json"
    private const val MAX_IMPORT_FILE_SIZE = 5L * 1024 * 1024 // 5 MB

    /**
     * Delay before deleting the temp export file after launching the share chooser.
     *
     * ACTION_SEND hands off to an external app whose result is not returned, so we
     * cannot delete on a completion callback. 60 s is generous: the chooser appears
     * instantly and the receiving app reads the content URI synchronously on user
     * selection, well within this window.
     */
    private const val EXPORT_CLEANUP_DELAY_MS = 60_000L

    // Preference keys
    private const val PREF_CALCULATION_METHOD = "calculation_method"
    private const val PREF_ASR_METHOD = "asr_method"
    private const val PREF_HIGH_LATITUDE_METHOD = "high_latitude_method"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    // ─── Export ───────────────────────────────────────────────────────────────

    /**
     * Exports all user data to a JSON file and shares it via an ACTION_SEND intent.
     *
     * The file is written to [context.cacheDir]/export/mosquednd_data.json and shared
     * using a FileProvider URI so no storage permissions are needed.
     *
     * @return true on success, false on failure (already logged)
     */
    fun exportToJson(context: Context): Boolean {
        return try {
            // Garbage collect stale files from previous runs
            garbageCollectStaleFiles(context)

            val exportData = buildExportData()
            val json = gson.toJson(exportData)

            val exportDir = File(context.cacheDir, EXPORT_DIR)
            exportDir.mkdirs()
            val file = File(exportDir, EXPORT_FILENAME)
            file.writeText(json, Charsets.UTF_8)

            LogHelper.logd(TAG, "Export file written: [REDACTED]")

            shareFile(context, file)

            // The chooser launches an external app with no result callback, so clean
            // the temp file up on a delay once the share target has had time to read it.
            scheduleTempFileCleanup(file)
            true
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Export failed", e)
            false
        }
    }

    /**
     * Garbage collects any leftover temp files from previous runs.
     */
    private fun garbageCollectStaleFiles(context: Context) {
        val exportDir = File(context.cacheDir, EXPORT_DIR)
        if (exportDir.exists() && exportDir.isDirectory) {
            val files = exportDir.listFiles()
            files?.forEach { file ->
                try {
                    if (file.delete()) {
                        LogHelper.logd(TAG, "Stale export file deleted: ${file.name}")
                    }
                } catch (e: Exception) {
                    LogHelper.loge(TAG, "Error deleting stale export file: ${file.name}", e)
                }
            }
        }
    }

    /**
     * Schedules deletion of the temp export file [EXPORT_CLEANUP_DELAY_MS] after the
     * share chooser is launched.
     *
     * Deletion is idempotent: [File.delete] returns false (rather than throwing) when
     * the file is already absent — e.g. cleared by the system or a previous run — so
     * this is always safe to call. Any unexpected error is caught and logged so the
     * share flow is never disrupted by cleanup.
     *
     * If deletion fails (e.g. locked by receiving app), retries once after 500ms.
     */
    private fun scheduleTempFileCleanup(file: File, isRetry: Boolean = false) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val deleted = file.delete()
                when {
                    deleted -> LogHelper.logd(TAG, "Temp export file deleted after share")
                    !file.exists() -> LogHelper.logd(TAG, "Temp export file already absent")
                    else -> {
                        LogHelper.logw(TAG, "Temp export file could not be deleted")
                        if (!isRetry) {
                            LogHelper.logd(TAG, "Retrying deletion...")
                            scheduleTempFileCleanup(file, true)
                        }
                    }
                }
            } catch (e: Exception) {
                LogHelper.loge(TAG, "Error deleting temp export file", e)
            }
        }, if (isRetry) 500L else EXPORT_CLEANUP_DELAY_MS)
    }

    /**
     * Builds the ExportData object from the current app state.
     */
    private fun buildExportData(): ExportData {
        val prefs = MosqueDNDApp.prefs

        val calcMethod = prefs.getString(PREF_CALCULATION_METHOD, "mwl") ?: "mwl"
        val asrMethod = prefs.getString(PREF_ASR_METHOD, "standard") ?: "standard"
        val highLatMethod = prefs.getString(PREF_HIGH_LATITUDE_METHOD, "none") ?: "none"
        val masterAlarm = PrayerAlarmPrefs.isMasterAlarmEnabled()

        // Build offsets map: prayerType.name.lowercase() -> offset
        val offsets = PrayerType.entries.associate { type ->
            type.name.lowercase() to PrayerAlarmPrefs.getOffset(type)
        }

        // Build alarms map: prayerType.name.lowercase() -> enabled
        val alarms = PrayerType.entries.associate { type ->
            type.name.lowercase() to PrayerAlarmPrefs.isAlarmEnabled(type)
        }

        val customAdhanUri = PrayerAlarmPrefs.getCustomAdhanUri()
        val mosques = MosqueRepository.getAllMosques()
        val theme = AppTheme.getCurrentTheme()

        // Build DND maps: PrayerType.name.lowercase() -> value
        val dndEnabled = PrayerAlarmPrefs.toDNDEnabledMap().mapKeys { it.key.name.lowercase() }
        val dndOffsets = PrayerAlarmPrefs.toDNDOffsetsMap().mapKeys { it.key.name.lowercase() }
        val dndDurations = PrayerAlarmPrefs.toDNDDurationsMap().mapKeys { it.key.name.lowercase() }

        return ExportData(
            exportVersion = EXPORT_VERSION,
            exportedAt = dateFormat.format(Date()),
            calculationMethod = calcMethod,
            asrMethod = asrMethod,
            highLatitudeMethod = highLatMethod,
            masterAlarmEnabled = masterAlarm,
            prayerOffsets = offsets,
            prayerAlarms = alarms,
            customAdhanUri = customAdhanUri,
            savedMosques = mosques,
            themePreference = theme,
            dndEnabled = dndEnabled,
            dndOffsets = dndOffsets,
            dndDurations = dndDurations
        )
    }

    /**
     * Shares the export file via an ACTION_SEND intent with application/json MIME type.
     */
    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, context.getString(com.mosquednd.R.string.export_data_title))
        )
        LogHelper.logd(TAG, "ACTION_SEND intent launched for export file")
    }

    // ─── Import ───────────────────────────────────────────────────────────────

    class VersionMismatchException(val foundVersion: Int, val expectedVersion: Int) : Exception("Unsupported export version $foundVersion. This app only supports version $expectedVersion.")

    /**
     * Parses a JSON string into an ExportData object.
     *
     * Validates that the JSON is well-formed and that its [exportVersion] exactly
     * matches [EXPORT_VERSION]. A stricter check than "is positive" is intentional:
     * importing a newer schema could silently drop or misinterpret fields, while a
     * much older one could import incomplete data.
     *
     * @param jsonString the raw JSON string to parse
     * @return Result containing either the parsed ExportData or an error message
     */
    fun parseImportJson(jsonString: String): Result<ExportData> {
        return try {
            // Quick size sanity check
            if (jsonString.length > MAX_IMPORT_FILE_SIZE) {
                return Result.failure(Exception("File too large (max 5 MB)"))
            }

            val exportData = gson.fromJson(jsonString, ExportData::class.java)
                ?: return Result.failure(Exception("Failed to parse JSON file"))

            // Strict version check: the schema version must exactly match the one
            // this app can read.
            if (exportData.exportVersion != EXPORT_VERSION) {
                return Result.failure(VersionMismatchException(exportData.exportVersion, EXPORT_VERSION))
            }

            Result.success(exportData)
        } catch (e: JsonParseException) {
            LogHelper.loge(TAG, "Malformed JSON during import parse", e)
            Result.failure(Exception("Invalid JSON format: ${e.message}"))
        } catch (e: VersionMismatchException) {
            // Re-throw to be caught by the general catch or handled by callers
            Result.failure(e)
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Unexpected error parsing import JSON", e)
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    /**
     * Generates a human-readable preview summary of the data to be imported.
     *
     * @return a map with counts per category: prayerSettings, alarms, offsets, mosques, theme
     */
    fun getImportPreview(exportData: ExportData): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        result["hasSettings"] = exportData.calculationMethod.isNotBlank() ||
                exportData.asrMethod.isNotBlank()
        result["alarmCount"] = exportData.prayerAlarms?.size ?: 0
        result["offsetCount"] = exportData.prayerOffsets?.size ?: 0
        result["mosqueCount"] = exportData.savedMosques?.size ?: 0
        result["hasTheme"] = exportData.themePreference.isNotBlank()
        result["hasCustomAdhan"] = exportData.customAdhanUri != null
        result["dndEnabledCount"] = exportData.dndEnabled?.size ?: 0
        result["dndOffsetsCount"] = exportData.dndOffsets?.size ?: 0
        result["dndDurationsCount"] = exportData.dndDurations?.size ?: 0

        return result
    }

    /**
     * Merges the imported ExportData into the current app state.
     *
     * Settings (calculation method, Asr method, master alarm, theme) are overwritten.
     * Offsets and alarms are overwritten per prayer.
     * Mosques are appended with ID-based deduplication.
     *
     * @param context the Android context
     * @param exportData the parsed export data to merge
     * @return a result message describing what was imported
     */
    fun mergeImportData(context: Context, exportData: ExportData): String {
        val prefs = MosqueDNDApp.prefs
        val importedCategories = mutableListOf<String>()

        // 1. Prayer calculation settings
        if (exportData.calculationMethod.isNotBlank()) {
            prefs.edit().putString(PREF_CALCULATION_METHOD, exportData.calculationMethod).apply()
            importedCategories.add("calculation method")
        }
        if (exportData.asrMethod.isNotBlank()) {
            prefs.edit().putString(PREF_ASR_METHOD, exportData.asrMethod).apply()
            importedCategories.add("Asr method")
        }
        if (exportData.highLatitudeMethod.isNotBlank()) {
            prefs.edit().putString(PREF_HIGH_LATITUDE_METHOD, exportData.highLatitudeMethod).apply()
            importedCategories.add("high latitude method")
        }

        // 2. Master alarm — written through PrayerAlarmPrefs so it lands in encrypted prefs
        PrayerAlarmPrefs.setMasterAlarmEnabled(exportData.masterAlarmEnabled)
        importedCategories.add("master alarm")

        // 3. Prayer offsets (null-safe: Gson may set null on non-nullable Kotlin types)
        if (exportData.prayerOffsets?.isNotEmpty() == true) {
            for ((prayerName, offset) in exportData.prayerOffsets) {
                val type = prayerTypeFromName(prayerName)
                if (type != null) {
                    PrayerAlarmPrefs.setOffset(type, offset)
                } else {
                    LogHelper.logw(TAG, "Unknown prayer type in offsets: [REDACTED]")
                }
            }
            importedCategories.add("${exportData.prayerOffsets.size} offsets")
        }

        // 4. Prayer alarm toggles (null-safe: Gson may set null on non-nullable Kotlin types)
        if (exportData.prayerAlarms?.isNotEmpty() == true) {
            for ((prayerName, enabled) in exportData.prayerAlarms) {
                val type = prayerTypeFromName(prayerName)
                if (type != null) {
                    PrayerAlarmPrefs.setAlarmEnabled(type, enabled)
                } else {
                    LogHelper.logw(TAG, "Unknown prayer type in alarms: [REDACTED]")
                }
            }
            importedCategories.add("${exportData.prayerAlarms.size} alarm toggles")
        }

        // 5. DND enabled toggles (null-safe)
        if (exportData.dndEnabled?.isNotEmpty() == true) {
            var dndEnabledCount = 0
            for ((prayerName, enabled) in exportData.dndEnabled) {
                val type = prayerTypeFromName(prayerName)
                if (type != null) {
                    PrayerAlarmPrefs.setDNDEnabled(type, enabled)
                    dndEnabledCount++
                } else {
                    LogHelper.logw(TAG, "Unknown prayer type in DND enabled: [REDACTED]")
                }
            }
            importedCategories.add("$dndEnabledCount DND toggles")
        }

        // 6. DND offsets (null-safe)
        if (exportData.dndOffsets?.isNotEmpty() == true) {
            var dndOffsetCount = 0
            for ((prayerName, offset) in exportData.dndOffsets) {
                val type = prayerTypeFromName(prayerName)
                if (type != null) {
                    PrayerAlarmPrefs.setDNDOffset(type, offset)
                    dndOffsetCount++
                } else {
                    LogHelper.logw(TAG, "Unknown prayer type in DND offsets: [REDACTED]")
                }
            }
            importedCategories.add("$dndOffsetCount DND offsets")
        }

        // 7. DND durations (null-safe)
        if (exportData.dndDurations?.isNotEmpty() == true) {
            var dndDurationCount = 0
            for ((prayerName, duration) in exportData.dndDurations) {
                val type = prayerTypeFromName(prayerName)
                if (type != null) {
                    PrayerAlarmPrefs.setDNDDuration(type, duration)
                    dndDurationCount++
                } else {
                    LogHelper.logw(TAG, "Unknown prayer type in DND durations: [REDACTED]")
                }
            }
            importedCategories.add("$dndDurationCount DND durations")
        }

        // 8. Custom Adhan URI
        if (exportData.customAdhanUri != null) {
            PrayerAlarmPrefs.setCustomAdhanUri(exportData.customAdhanUri)
            importedCategories.add("custom Adhan")
        }

        // 9. Mosques (append with ID-based dedup; null-safe for Gson-deserialized data)
        if (exportData.savedMosques?.isNotEmpty() == true) {
            val existingMosques = MosqueRepository.getAllMosques()
            val existingIds = existingMosques.map { it.id }.toSet()
            var addedCount = 0
            var skippedCount = 0

            for (mosque in exportData.savedMosques) {
                if (mosque.id in existingIds) {
                    skippedCount++
                    LogHelper.logd(TAG, "Skipping duplicate mosque: [REDACTED] (id=[REDACTED])")
                } else {
                    MosqueRepository.addMosque(mosque)
                    addedCount++
                }
            }

            if (addedCount > 0) {
                importedCategories.add("${addedCount} mosques")
            }
            if (skippedCount > 0) {
                importedCategories.add("${skippedCount} duplicates skipped")
            }
        }

        // 10. Theme preference
        if (exportData.themePreference.isNotBlank()) {
            AppTheme.setThemePreference(context, exportData.themePreference)
            importedCategories.add("theme")
        }

        return if (importedCategories.isEmpty()) {
            "Nothing to import"
        } else {
            "Imported: ${importedCategories.joinToString(", ")}"
        }
    }

    /**
     * Resolves a lowercase prayer type name to its PrayerType enum value.
     * Returns null for unknown names (e.g. "sunrise" which is a valid type
     * but also skipped in some alarm contexts).
     */
    private fun prayerTypeFromName(name: String): PrayerType? {
        return PrayerType.entries.find { it.name.lowercase() == name.lowercase() }
    }
}
