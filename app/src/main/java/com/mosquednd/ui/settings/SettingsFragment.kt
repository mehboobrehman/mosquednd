package com.mosquednd.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.mosquednd.MosqueDNDApp
import com.mosquednd.R
import com.mosquednd.data.ExportData
import com.mosquednd.data.ImportExportManager
import com.mosquednd.prayer.AsrMethod
import com.mosquednd.prayer.CalculationMethod
import com.mosquednd.prayer.PrayTimesCalculator
import com.mosquednd.prayer.PrayerType
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.service.AlarmScheduler
import com.mosquednd.theme.AppTheme
import com.mosquednd.ui.onboarding.OnboardingActivity
import com.mosquednd.util.ExactAlarmHelper
import com.mosquednd.util.AgentLogger
import com.mosquednd.geofence.GeofenceConfig
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.LogHelper
import com.mosquednd.whatsnew.WhatsNewBottomSheetFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.Manifest
import android.app.AlarmManager
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.Date

class SettingsFragment : PreferenceFragmentCompat() {

    companion object {
        private const val TAG = "SettingsFragment"
        private const val PREF_CALCULATION_METHOD = "calculation_method"
        private const val PREF_ASR_METHOD = "asr_method"
    }

    private var ringtonePickerLauncher: ActivityResultLauncher<Intent>? = null
    private var importFileLauncher: ActivityResultLauncher<Array<String>>? = null

    /**
     * Notification permission launcher — fires only when the user enables
     * Adhan/alarm notifications (gated behind feature access, not on cold launch).
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "POST_NOTIFICATIONS granted for alarms")
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS denied — notifications will be silently suppressed")
            Toast.makeText(
                requireContext(),
                "Notification permission needed for Adhan alerts",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Requests POST_NOTIFICATIONS permission (API 33+) when the user enables
     * Adhan/alarm notifications. No-op on older API levels.
     */
    private fun requestNotificationForAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS for alarms")
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Checks SCHEDULE_EXACT_ALARM permission (API 31+) and shows a settings
     * dialog if the permission is not granted. Delegates to ExactAlarmHelper
     * to avoid duplication across fragments.
     */
    private fun checkExactAlarmForAlarms(): Boolean {
        return ExactAlarmHelper.checkExactAlarm(requireContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_CREATE", mapOf("fragment" to "SettingsFragment"))
        super.onCreate(savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_CREATE_PREFERENCES", mapOf("fragment" to "SettingsFragment"))
        setPreferencesFromResource(R.xml.preferences, rootKey)
        Log.d(TAG, "Preferences inflated from R.xml.preferences")

        setupThemePreferenceListener()
        setupSectPreferenceListener()
        setupCalculationMethodListener()
        setupAsrMethodListener()
        setupMasterAlarmListener()
        setupAutoDndListener()
        setupDndDurationListener()
        setupDetectionRadiusListener()
        setupHijriOffsetListener()
        setupRingtonePreference()
        setupGoogleAuthPreference()
        setupDataPreferences()
        setupOnboardingPreference()
        setupReliabilityPreference()
        setupLogServerPreference()
        setupAboutVersionPreferences()
        setupWhatsNewPreference()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_VIEW_CREATED", mapOf("fragment" to "SettingsFragment"))
        super.onViewCreated(view, savedInstanceState)
        listView.clipToPadding = false
        listView.setPadding(
            listView.paddingLeft,
            listView.paddingTop,
            listView.paddingRight,
            resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
        )

        // Wire inline help buttons that are already visible, then listen for more
        wireHelpButtons()
        listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {}
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                wireHelpButtons()
            }
        })
    }

    override fun onResume() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_RESUME", mapOf("fragment" to "SettingsFragment"))
        super.onResume()
        updateRingtoneSummary()
        updateGoogleAuthPreferenceSummary()
    }

    override fun onPause() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_PAUSE", mapOf("fragment" to "SettingsFragment"))
        super.onPause()
    }

    override fun onDestroyView() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_DESTROY_VIEW", mapOf("fragment" to "SettingsFragment"))
        super.onDestroyView()
    }

    override fun onDestroy() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_DESTROY", mapOf("fragment" to "SettingsFragment"))
        super.onDestroy()
    }

    private fun setupThemePreferenceListener() {
        val themePreference = findPreference<ListPreference>("pref_theme") ?: return
        val currentValue = MosqueDNDApp.prefs.getString(MosqueDNDApp.PREFS_THEME, MosqueDNDApp.THEME_SYSTEM)
            ?: MosqueDNDApp.THEME_SYSTEM
        val currentIndex = themePreference.entryValues.indexOfFirst { it == currentValue }
        if (currentIndex >= 0) {
            themePreference.summary = themePreference.entries[currentIndex]
        }

        themePreference.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as String
            Log.d(TAG, "Theme changed to: $value")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to "pref_theme", "value" to value))
            AppTheme.setThemePreference(requireContext(), value)
            val index = themePreference.entryValues.indexOfFirst { it == value }
            if (index >= 0) {
                themePreference.summary = themePreference.entries[index]
            }
            requireActivity().recreate()
            true
        }
    }

    private fun setupSectPreferenceListener() {
        val sectPreference = findPreference<ListPreference>("pref_sect")
        sectPreference?.setOnPreferenceChangeListener { _, newValue ->
            val sect = newValue as String
            Log.d(TAG, "School changed to: $sect")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to "pref_sect", "value" to sect))
            applySect(sect)
            true
        }
    }

    /**
     * Maps a school-of-thought value to calculation method and Asr method preferences.
     *
     * - Hanafi: sets Asr=HANAFI, preserves calc method
     * - Hanbali/Maliki/Shafi'i/Zaydi/Ibadi: sets Asr=SHAFII, preserves calc method
     * - Ja'fari (Shia): sets Calc=JAFARI + Asr=SHAFII
     * - Custom: leaves both unchanged (user sets them manually)
     */
    private fun applySect(sect: String) {
        val prefs = MosqueDNDApp.prefs

        when (sect) {
            "hanafi" -> {
                prefs.edit().putString(PREF_ASR_METHOD, "hanafi").apply()
                Log.d(TAG, "School: Hanafi — Asr set to HANAFI, calculation method preserved")
            }
            "hanbali", "maliki", "shafii", "zaydi", "ibadi" -> {
                prefs.edit().putString(PREF_ASR_METHOD, "standard").apply()
                Log.d(TAG, "School: $sect — Asr set to SHAFII, calculation method preserved")
            }
            "jafari" -> {
                prefs.edit()
                    .putString(PREF_CALCULATION_METHOD, "jafari")
                    .putString(PREF_ASR_METHOD, "standard")
                    .apply()
                Log.d(TAG, "School: Jafari — Calulation set to JAFARI, Asr set to SHAFII")
            }
            "custom" -> {
                Log.d(TAG, "School: Custom — calc and Asr methods left unchanged (manual control)")
            }
            else -> {
                Log.w(TAG, "Unknown school: $sect — leaving calc and Asr methods unchanged")
            }
        }

        // Notify downstream (alarms, widget, fragment recalculation)
        propagateSettingsChange()
    }

    private fun setupCalculationMethodListener() {
        val calcMethodPreference = findPreference<ListPreference>("calculation_method")
        calcMethodPreference?.setOnPreferenceChangeListener { _: Preference, newValue: Any ->
            Log.d(TAG, "Calculation method changed to: $newValue")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to "calculation_method", "value" to newValue.toString()))
            propagateSettingsChange()
            true
        }
    }

    private fun setupAsrMethodListener() {
        val asrMethodPreference = findPreference<ListPreference>("asr_method")
        asrMethodPreference?.setOnPreferenceChangeListener { _: Preference, newValue: Any ->
            Log.d(TAG, "Asr method changed to: $newValue")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to "asr_method", "value" to newValue.toString()))
            propagateSettingsChange()
            true
        }
    }

    private fun setupMasterAlarmListener() {
        val masterAlarmPref = findPreference<SwitchPreferenceCompat>("master_alarm_enabled")
        masterAlarmPref?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            val context = requireContext()
            Log.d(TAG, "Master alarm toggled to: $enabled")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to "master_alarm_enabled", "value" to enabled))

            // Gate notification and exact-alarm permissions behind alarm enable
            if (enabled) {
                requestNotificationForAlarms()
                checkExactAlarmForAlarms() // returns false if dialog shown; alarm scheduler falls back to setWindow()
            }

            PrayerAlarmPrefs.setMasterAlarmEnabled(enabled)

            if (enabled) {
                try {
                    val prefs = MosqueDNDApp.prefs
                    val calcMethod = CalculationMethod.fromPreferenceValue(
                        prefs.getString(PREF_CALCULATION_METHOD, "mwl") ?: "mwl"
                    )
                    val asrMethod = AsrMethod.fromPreferenceValue(
                        prefs.getString(PREF_ASR_METHOD, "standard") ?: "standard"
                    )
                    val (lat, lng) = LocationProvider.getLastKnownLocation()
                    val offsets = PrayerAlarmPrefs.toOffsetsMap()
                    val timesMap = PrayTimesCalculator.calculate(
                        date = Date(),
                        latitude = lat,
                        longitude = lng,
                        method = calcMethod,
                        asrMethod = asrMethod,
                        offsets = offsets
                    )
                    val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes().toSet()
                    AlarmScheduler.rescheduleAll(context, timesMap, enabledTypes)
                    Log.d(TAG, "Prayer alarms rescheduled via master toggle ON")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule on master toggle enable", e)
                }
            } else {
                AlarmScheduler.cancelAllPrayers(context)
                Log.d(TAG, "All prayer alarms cancelled via master toggle OFF")
            }

            triggerWidgetUpdate(context)
            true
        }
    }

    private fun setupAutoDndListener() {
        val autoDndPref = findPreference<SwitchPreferenceCompat>("pref_auto_dnd_enabled")
        // Disable PreferenceFragmentCompat auto-persistence: the value lives in the
        // encrypted store (via PrayerAlarmPrefs), not the default SharedPreferences, so
        // that backup rules keep it out of cloud/device backups.
        autoDndPref?.let { pref ->
            pref.isPersistent = false
            pref.isChecked = PrayerAlarmPrefs.isAutoDNDEnabled()
            pref.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                PrayerAlarmPrefs.setAutoDNDEnabled(enabled)
                Log.d(TAG, "Auto DND toggled to: $enabled")
                LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to "pref_auto_dnd_enabled", "value" to enabled))
                true
            }
        }
    }

    private fun setupDndDurationListener() {
        val dndDurationPref = findPreference<ListPreference>("pref_auto_dnd_duration_min")
        // isPersistent=false so the value is stored only in the encrypted store.
        dndDurationPref?.let { pref ->
            pref.isPersistent = false
            pref.value = PrayerAlarmPrefs.getAutoDNDDurationMin().toString()
            pref.setOnPreferenceChangeListener { _, newValue ->
                val minutes = (newValue as? String)?.toIntOrNull() ?: 30
                PrayerAlarmPrefs.setAutoDNDDurationMin(minutes)
                Log.d(TAG, "DND duration changed to: $newValue minutes")
                LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to "pref_auto_dnd_duration_min", "value" to (newValue ?: "null")))
                true
            }
        }
    }

    private fun setupDetectionRadiusListener() {
        val radiusPref = findPreference<SeekBarPreference>("pref_detection_radius_m")
        val warningPref = findPreference<Preference>("pref_detection_radius_warning")
        radiusPref?.let { pref ->
            // Set initial summary from current value
            val current = MosqueDNDApp.prefs.getInt("pref_detection_radius_m", GeofenceConfig.DEFAULT_DETECTION_RADIUS_M)
            pref.summary = getString(R.string.pref_detection_radius_summary, current)
            updateRadiusWarning(current, warningPref)

            pref.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as Int
                pref.summary = getString(R.string.pref_detection_radius_summary, value)
                updateRadiusWarning(value, warningPref)
                Log.d(TAG, "Detection radius changed to: ${value}m")
                LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to "pref_detection_radius_m", "value" to value))
                true
            }
        }
    }

    private fun updateRadiusWarning(radiusMeters: Int, warningPref: Preference?) {
        if (warningPref == null) {
            Log.w(TAG, "Detection radius warning preference not found")
            return
        }
        val isOutsideRange = radiusMeters < 10 || radiusMeters > 500
        warningPref.isVisible = isOutsideRange
        Log.d(TAG, "Radius warning visibility: ${warningPref.isVisible} (radius=${radiusMeters}m)")
    }

    private fun setupHijriOffsetListener() {
        val hijriOffsetPref = findPreference<ListPreference>("hijri_date_offset")
        hijriOffsetPref?.let { pref ->
            // Set initial summary from current value so it shows the selected offset
            val currentValue = MosqueDNDApp.prefs.getString("hijri_date_offset", "0") ?: "0"
            val currentIndex = pref.entryValues.indexOfFirst { it == currentValue }
            if (currentIndex >= 0) {
                pref.summary = pref.entries[currentIndex]
            }

            pref.setOnPreferenceChangeListener { _, newValue ->
                val valueStr = newValue as String
                val index = pref.entryValues.indexOfFirst { it == valueStr }
                if (index >= 0) {
                    pref.summary = pref.entries[index]
                }
                Log.d(TAG, "Hijri offset changed to: $valueStr")
                LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to "hijri_date_offset", "value" to valueStr))
                true
            }
        }
    }

    private fun wireHelpButtons() {
        data class HelpData(val titleRes: Int, val messageRes: Int)
        val helpMap = mapOf(
            "pref_sect" to HelpData(R.string.pref_sect_help_title, R.string.pref_sect_help_message),
            "calculation_method" to HelpData(R.string.help_calc_method_title, R.string.help_calc_method_message),
            "asr_method" to HelpData(R.string.help_asr_method_title, R.string.help_asr_method_message),
            "adhan_notifications" to HelpData(R.string.help_adhan_notifications_title, R.string.help_adhan_notifications_message),
            "geofence_enabled" to HelpData(R.string.help_geofence_enabled_title, R.string.help_geofence_enabled_message),
            "master_alarm_enabled" to HelpData(R.string.help_master_alarm_enabled_title, R.string.help_master_alarm_enabled_message),
            "pref_onboarding" to HelpData(R.string.help_onboarding_title, R.string.help_onboarding_message),
            "pref_auto_dnd_duration_min" to HelpData(R.string.help_dnd_duration_title, R.string.help_dnd_duration_message),
            "pref_detection_radius_m" to HelpData(R.string.help_detection_radius_title, R.string.help_detection_radius_message),
            "hijri_date_offset" to HelpData(R.string.hijri_date_offset_help_title, R.string.hijri_date_offset_help_message),
        )

        for (i in 0 until listView.childCount) {
            val child = listView.getChildAt(i)
            val helpButton = child?.findViewById<ImageButton>(R.id.help_button) ?: continue
            if (helpButton.tag != null) continue // Already wired

            val position = listView.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            val adapter = listView.adapter
            if (adapter !is PreferenceGroupAdapter) continue
            val pref = adapter.getItem(position) as? Preference ?: continue
            val helpData = helpMap[pref.key] ?: continue

            helpButton.tag = "wired"
            helpButton.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(helpData.titleRes)
                    .setMessage(helpData.messageRes)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun setupRingtonePreference() {
        val ringtonePref = findPreference<Preference>("custom_adhan_ringtone")

        // Register the activity result launcher for the ringtone picker
        ringtonePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                        Uri::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
                if (uri != null) {
                    // User selected a custom ringtone
                    PrayerAlarmPrefs.setCustomAdhanUri(uri.toString())
                    val name = try {
                        RingtoneManager.getRingtone(requireContext(), uri)
                            ?.getTitle(requireContext()) ?: getString(R.string.adhan_ringtone_none)
                    } catch (e: Exception) {
                        getString(R.string.adhan_ringtone_none)
                    }
                    ringtonePref?.summary = name
                    Log.d(TAG, "Custom Adhan ringtone selected: $name")
                } else {
                    // User selected default ringtone
                    PrayerAlarmPrefs.setCustomAdhanUri(null)
                    ringtonePref?.summary = getString(R.string.adhan_ringtone_none)
                    Log.d(TAG, "Adhan ringtone reset to default")
                }
            }
        }

        ringtonePref?.setOnPreferenceClickListener { _ ->
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                )
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TITLE,
                    getString(R.string.adhan_ringtone_title)
                )
            }
            ringtonePickerLauncher?.launch(intent)
            true
        }

        updateRingtoneSummary()
    }

    // ─── Import / Export ────────────────────────────────────────────────────────

    /**
     * Re-launches the OnboardingActivity so the user can replay the feature walkthrough.
     */
    private fun setupOnboardingPreference() {
        val onboardingPref = findPreference<Preference>("pref_onboarding")
        onboardingPref?.setOnPreferenceClickListener { _ ->
            Log.d(TAG, "Onboarding preference tapped — launching OnboardingActivity")
            OnboardingActivity.start(requireContext())
            true
        }
    }

    private fun setupGoogleAuthPreference() {
        val googleAuthPref = findPreference<Preference>("google_sign_in")
        googleAuthPref?.setOnPreferenceClickListener { _ ->
            val context = requireContext()
            if (com.mosquednd.auth.GoogleAuthManager.isSignedin(context)) {
                val user = com.mosquednd.auth.GoogleAuthManager.getSignedInUser(context)
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.google_sign_out_title)
                    .setMessage(getString(R.string.google_sign_out_confirm))
                    .setPositiveButton(R.string.remove) { _, _ ->
                        com.mosquednd.auth.GoogleAuthManager.signOut(context)
                        updateGoogleAuthPreferenceSummary()
                        Toast.makeText(context, R.string.google_sign_out_success, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                lifecycleScope.launch {
                    try {
                        val user = com.mosquednd.auth.GoogleAuthManager.signIn(context)
                        updateGoogleAuthPreferenceSummary()
                        Toast.makeText(
                            context,
                            getString(R.string.google_sign_in_success, user.email),
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Google Sign-In failed", e)
                        Toast.makeText(
                            context,
                            getString(R.string.google_sign_in_failed, e.localizedMessage ?: "Unknown error"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            true
        }
        updateGoogleAuthPreferenceSummary()
    }

    private fun updateGoogleAuthPreferenceSummary() {
        val googleAuthPref = findPreference<Preference>("google_sign_in") ?: return
        val context = requireContext()
        if (com.mosquednd.auth.GoogleAuthManager.isSignedin(context)) {
            val user = com.mosquednd.auth.GoogleAuthManager.getSignedInUser(context)
            val email = user?.email ?: ""
            googleAuthPref.title = getString(R.string.google_sign_out_title)
            googleAuthPref.summary = getString(R.string.google_sign_in_summary_signed_in, email)
        } else {
            googleAuthPref.title = getString(R.string.google_sign_in_title)
            googleAuthPref.summary = getString(R.string.google_sign_in_summary_signed_out)
        }
    }
    private fun setupDataPreferences() {
        // Register the SAF file picker for JSON files
        importFileLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                handleImportFile(uri)
            } else {
                Log.d(TAG, "Import file picker cancelled")
            }
        }

        // Export Data
        val exportPref = findPreference<Preference>("export_data")
        exportPref?.setOnPreferenceClickListener { _ ->
            handleExport()
            true
        }

        // Import Data
        val importPref = findPreference<Preference>("import_data")
        importPref?.setOnPreferenceClickListener { _ ->
            importFileLauncher?.launch(arrayOf("application/json", "*/*"))
            true
        }
    }

    /**
     * Handles the Export Data action.
     */
    private fun handleExport() {
        val context = requireContext()
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "DATA_EXPORT_INIT")
        val success = ImportExportManager.exportToJson(context)
        if (success) {
            Log.d(TAG, "Export completed successfully")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "DATA_EXPORT_SUCCESS")
        } else {
            Log.e(TAG, "Export failed")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "DATA_EXPORT_FAILED")
            Toast.makeText(context, getString(R.string.export_error, "See log for details"), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Handles the import file selected from the SAF file picker.
     * Reads the file content, parses, validates, shows preview dialog, and merges.
     */
    private fun handleImportFile(uri: Uri) {
        val context = requireContext()
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "DATA_IMPORT_INIT", mapOf("uri" to uri.toString()))

        // Read file content from content provider
        val jsonString: String = try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open file")
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read import file", e)
            showImportErrorToast(getString(R.string.import_error, e.message ?: "Unknown error"))
            return
        }

        // Parse and validate
        val parseResult = ImportExportManager.parseImportJson(jsonString)
        if (parseResult.isFailure) {
            val error = parseResult.exceptionOrNull()
            if (error is ImportExportManager.VersionMismatchException) {
                showImportErrorToast(getString(R.string.import_version_mismatch, error.foundVersion, error.expectedVersion))
            } else {
                val errorMsg = error?.message ?: "Unknown error"
                Log.e(TAG, "Import parse failed: $errorMsg")
                showImportErrorToast(
                    getString(R.string.import_invalid_format) + "\n\n$errorMsg"
                )
            }
            return
        }

        val exportData = parseResult.getOrThrow()

        // Build preview summary
        val preview = ImportExportManager.getImportPreview(exportData)
        val settingsCount = if (preview["hasSettings"] == true) 1 else 0
        val alarmCount = (preview["alarmCount"] as? Int) ?: 0
        val offsetCount = (preview["offsetCount"] as? Int) ?: 0
        val mosqueCount = (preview["mosqueCount"] as? Int) ?: 0
        val themeLabel = if (preview["hasTheme"] == true) exportData.themePreference else "none"

        // Show preview confirmation dialog
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.import_confirm_title)
            .setMessage(
                getString(
                    R.string.import_confirm_message,
                    settingsCount,
                    alarmCount,
                    offsetCount,
                    mosqueCount,
                    themeLabel
                )
            )
            .setPositiveButton(android.R.string.ok) { _, _ ->
                performImport(context, exportData)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Performs the data merge after user confirmation.
     */
    private fun performImport(context: Context, exportData: ExportData) {
        val resultMessage = ImportExportManager.mergeImportData(context, exportData)
        Log.d(TAG, "Import merge result: $resultMessage")
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "DATA_IMPORT_SUCCESS", mapOf("result" to resultMessage))

        // Show success/result toast
        Toast.makeText(context, resultMessage, Toast.LENGTH_LONG).show()

        // Trigger setting propagation (alarm reschedule, widget update)
        propagateSettingsChange()
    }

    /**
     * Shows an error dialog for import failures.
     */
    private fun showImportErrorToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun updateRingtoneSummary() {
        val ringtonePref = findPreference<Preference>("custom_adhan_ringtone")
        val uriString = PrayerAlarmPrefs.getCustomAdhanUri()
        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                val name = RingtoneManager.getRingtone(requireContext(), uri)
                    ?.getTitle(requireContext()) ?: getString(R.string.adhan_ringtone_none)
                ringtonePref?.summary = name
            } catch (e: Exception) {
                ringtonePref?.summary = getString(R.string.adhan_ringtone_none)
            }
        } else {
            ringtonePref?.summary = getString(R.string.adhan_ringtone_none)
        }
    }

    /**
     * Propagates prayer calculation settings changes to:
     * 1. Widget — via AppWidgetManager broadcast
     * 2. Prayer alarms — recalculated and rescheduled via AlarmScheduler
     * 3. (PrayerTimesFragment recalculates via SharedPreferences listener in onResume)
     */
    private fun propagateSettingsChange() {
        val context = requireContext()

        // 1. Trigger widget update
        triggerWidgetUpdate(context)

        // 2. Reschedule prayer alarms with new calculation settings
        reschedulePrayerAlarms(context)
    }

    /**
     * Broadcasts ACTION_APPWIDGET_UPDATE so the PrayerTimesWidget refreshes its display.
     */
    private fun triggerWidgetUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, com.mosquednd.widget.PrayerTimesWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_container)
            val updateIntent = android.content.Intent(
                AppWidgetManager.ACTION_APPWIDGET_UPDATE,
                null,
                context,
                com.mosquednd.widget.PrayerTimesWidget::class.java
            ).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(updateIntent)
            Log.d(TAG, "Widget update broadcast sent to ${appWidgetIds.size} widgets")
        } else {
            Log.d(TAG, "No widget instances to update")
        }
    }

    /**
     * Recalculates today's prayer times with current settings and offsets, then reschedules
     * only the enabled prayer alarms via AlarmScheduler.rescheduleAll().
     */
    private fun reschedulePrayerAlarms(context: Context) {
        if (!PrayerAlarmPrefs.isMasterAlarmEnabled()) {
            Log.d(TAG, "Master alarm OFF — skipping reschedule from settings")
            return
        }
        try {
            val prefs = MosqueDNDApp.prefs
            val calcMethodStr = prefs.getString(PREF_CALCULATION_METHOD, "mwl") ?: "mwl"
            val asrMethodStr = prefs.getString(PREF_ASR_METHOD, "standard") ?: "standard"

            val calcMethod = CalculationMethod.fromPreferenceValue(calcMethodStr)
            val asrMethod = AsrMethod.fromPreferenceValue(asrMethodStr)

            val (lat, lng) = LocationProvider.getLastKnownLocation()
            val offsets = PrayerAlarmPrefs.toOffsetsMap()

            val timesMap = PrayTimesCalculator.calculate(
                date = Date(),
                latitude = lat,
                longitude = lng,
                method = calcMethod,
                asrMethod = asrMethod,
                offsets = offsets
            )

            val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes().toSet()
            AlarmScheduler.rescheduleAll(context, timesMap, enabledTypes)
            // Also reschedule DND alarms to keep in sync
            val dndEnabledTypes = PrayerType.entries.filter { it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it) }.toSet()
            AlarmScheduler.cancelDNDAlarms(context)
            AlarmScheduler.scheduleDNDAlarms(context, timesMap, dndEnabledTypes)
            Log.d(TAG, "Prayer alarms rescheduled with method=$calcMethod, asr=$asrMethod, offsets=$offsets, enabled=$enabledTypes, dndEnabled=$dndEnabledTypes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule prayer alarms", e)
        }
    }

    /**
     * Developer/debug entry: manual override for the AgentLogger server IP/URL.
     * The WiFi log server (scripts/log-server.js on a PC) is normally found by scanning
     * the phone's OWN subnet only, so when phone and PC are on different subnets the
     * discovery can never find it. This lets the user pin the server URL explicitly.
     *
     * Accepts "192.168.105.79", "192.168.105.79:5000", or "http://192.168.105.79:5000".
     * The value is persisted by AgentLogger and used on every launch WITHOUT subnet
     * scanning, and any buffered logs are flushed immediately.
     */
    private fun setupLogServerPreference() {
        val logServerPref = findPreference<EditTextPreference>("log_server_ip")
        logServerPref?.let { pref ->
            pref.isPersistent = false // value lives exclusively in AgentLogger's prefs file
            val current = AgentLogger.getServerIp(requireContext())
            pref.text = current ?: ""
            pref.summary = if (current != null) {
                "Current: $current\nAuto-discovery scans the phone's subnet only. If the PC log server is on another subnet, enter it here."
            } else {
                "Auto-discovery scans the phone's subnet only. If the PC log server is on another subnet, enter it here, e.g. http://192.168.105.79:5000"
            }

            pref.setOnPreferenceChangeListener { _, newValue ->
                val value = (newValue as? String)?.trim().orEmpty()
                if (value.isEmpty()) {
                    AgentLogger.clearServerIp(requireContext())
                    Toast.makeText(requireContext(), "Auto-discovery re-enabled", Toast.LENGTH_SHORT).show()
                    pref.text = ""
                    pref.summary = "Auto-discovery scans the phone's subnet only. If the PC log server is on another subnet, enter it here, e.g. http://192.168.105.79:5000"
                } else {
                    AgentLogger.setServerIpFromUser(value)
                    Toast.makeText(requireContext(), "Log server set — buffered logs flushed", Toast.LENGTH_SHORT).show()
                    pref.text = value
                }
                true
            }
        }
    }

    /**
     * EditTextPreference needs a custom dialog to display a free-text field.
     * Must be overridden here because we attach dialogs via a host that is not
     * the default PreferenceFragmentCompat fragment manager behavior.
     */
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is EditTextPreference) {
            val dialog = EditTextPreferenceDialogFragmentCompat.newInstance(preference.key)
            dialog.setTargetFragment(this, 0)
            dialog.show(parentFragmentManager, "androidx.preference.PreferenceFragment.EDIT_TEXT")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun setupReliabilityPreference() {
        val batteryPref = findPreference<Preference>("pref_battery_optimization")
        batteryPref?.setOnPreferenceClickListener {
            val context = requireContext()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Background Reliability")
                        .setMessage("To ensure Adhan alerts work reliably, please disable battery optimizations for MosqueDND.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    startActivity(intent)
                                } catch (e2: Exception) {
                                    Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    Toast.makeText(context, "Battery optimizations already disabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Not required on this Android version", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun setupWhatsNewPreference() {
        val whatsNewPref = findPreference<Preference>("pref_whats_new")
        whatsNewPref?.setOnPreferenceClickListener {
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.NAVIGATION, "WHATS_NEW_CLICKED", mapOf("source" to "settings"))
            WhatsNewBottomSheetFragment.show(parentFragmentManager)
            true
        }
    }

    private fun setupAboutVersionPreferences() {
        val versionInfo = com.mosquednd.AppVersionInfo.getCurrent()

        val versionPref = findPreference<Preference>("pref_app_version")
        versionPref?.summary = "${versionInfo.versionName} (${versionInfo.versionCode})"

        val hashPref = findPreference<Preference>("pref_build_hash")
        hashPref?.summary = versionInfo.gitCommitHash

        val timestampPref = findPreference<Preference>("pref_build_timestamp")
        timestampPref?.summary = versionInfo.buildTimestamp

        val envPref = findPreference<Preference>("pref_build_env")
        envPref?.summary = versionInfo.environment.replaceFirstChar { it.uppercase() }
    }
}
