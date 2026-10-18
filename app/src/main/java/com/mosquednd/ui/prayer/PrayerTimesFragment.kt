package com.mosquednd.ui.prayer

import java.util.Calendar
import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mosquednd.MosqueDNDApp
import com.mosquednd.R
import com.mosquednd.calendar.IslamicCalendar
import com.mosquednd.databinding.FragmentPrayerTimesBinding
import com.mosquednd.prayer.AsrMethod
import com.mosquednd.prayer.CalculationMethod
import com.mosquednd.prayer.PrayTimesCalculator
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerDNDManager
import com.mosquednd.prayer.PrayerTime
import com.mosquednd.prayer.PrayerType
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mosquednd.receiver.DailyRecalculationHelper
import com.mosquednd.service.AlarmScheduler
import com.mosquednd.util.DNDController
import com.mosquednd.util.ExactAlarmHelper
import com.mosquednd.util.LocationProvider
import com.mosquednd.util.LogHelper
import com.mosquednd.util.formatOffset
import com.mosquednd.widget.PrayerTimesWidget
import java.util.concurrent.TimeUnit

class PrayerTimesFragment : Fragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var _binding: FragmentPrayerTimesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PrayerTimesAdapter
    private var lastTimesMap: Map<PrayerType, PrayerTime>? = null
    private var lastSeenDayOfYear: Int = -1

    private var prayerHelpPopup: PopupWindow? = null
    private var lastPopupDismissMs: Long = 0L

    // ── Adhan/DND overlap validation debounce ──
    private var lastOverlapToastTime: Long = 0L

    /**
     * Notification permission launcher — fires only when the user enables
     * an Adhan prayer alarm (gated behind feature access, not on cold launch).
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "POST_NOTIFICATIONS granted for prayer alarms")
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
     * a per-prayer Adhan alarm. No-op on older API levels.
     */
    private fun requestNotificationForAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS for prayer alarms")
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Checks SCHEDULE_EXACT_ALARM permission (API 31+) and shows a settings
     * dialog if the permission is not granted. Delegates to ExactAlarmHelper
     * to avoid duplication with SettingsFragment.
     */
    private fun checkExactAlarmForAlarms(): Boolean {
        return ExactAlarmHelper.checkExactAlarm(requireContext())
    }

    companion object {
        private const val TAG = "PrayerTimesFragment"
        private const val PREF_CALCULATION_METHOD = "calculation_method"
        private const val PREF_ASR_METHOD = "asr_method"
        private const val PREF_LAST_LOCATION_VERSION = "prayer_fragment_last_location_version"
        private const val PREF_LAST_TIMEZONE_ID = "prayer_fragment_last_timezone_id"
        private const val POPUP_DISMISS_DEBOUNCE_MS = 300L
        private const val PREF_SECT = "pref_sect"
        private const val OVERLAP_TOAST_DEBOUNCE_MS = 300L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_CREATE", mapOf("fragment" to "PrayerTimesFragment"))
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_CREATE_VIEW", mapOf("fragment" to "PrayerTimesFragment"))
        _binding = FragmentPrayerTimesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Apply FLAG_SECURE to prevent screenshots/screen recording of sensitive prayer data (data security, Play Store compliance)
        requireActivity().window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // Forward alarm-schedule errors to user via Snackbar
        AlarmScheduler.setAlarmScheduleErrorCallback { message ->
            if (isAdded && _binding != null) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
        }

        adapter = PrayerTimesAdapter()
        adapter.onToggleChanged = onToggleChanged@{ prayerType, enabled ->
            // Validate Adhan/DND overlap when enabling alarm
            if (enabled && PrayerAlarmPrefs.isDNDEnabled(prayerType) &&
                hasAdhanDNDOverlap(prayerType,
                    PrayerAlarmPrefs.getDNDOffset(prayerType), PrayerAlarmPrefs.getDNDDuration(prayerType))
            ) {
                Log.w(TAG, "Alarm enable for $prayerType blocked — Adhan/DND overlap")
                adapter.notifyDataSetChanged()
                return@onToggleChanged
            } else {
                // Gate notification and exact-alarm permissions behind per-prayer alarm toggle
                if (enabled) {
                    requestNotificationForAlarms()
                    checkExactAlarmForAlarms()
                }

                PrayerAlarmPrefs.setAlarmEnabled(prayerType, enabled)
                if (PrayerAlarmPrefs.isMasterAlarmEnabled()) {
                    val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes().toSet()
                    lastTimesMap?.let { timesMap ->
                        AlarmScheduler.rescheduleAll(requireContext(), timesMap, enabledTypes)
                        // Also reschedule DND alarms to keep in sync
                        val dndEnabledTypes = PrayerType.entries.filter { it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it) }.toSet()
                        AlarmScheduler.cancelDNDAlarms(requireContext())
                        AlarmScheduler.scheduleDNDAlarms(requireContext(), timesMap, dndEnabledTypes)
                    }
                } else {
                    Log.d(TAG, "Master alarm OFF — skipping alarm reschedule from toggle")
                    AlarmScheduler.cancelAlarm(requireContext(), prayerType.ordinal)
                }
                // Refresh display to reflect updated alarm state
                calculatePrayerTimes()
            }
        }
        adapter.onRowClick = { prayerType ->
            adapter.toggleExpanded(prayerType)
        }
        adapter.onOffsetChanged = { type, currentOffset ->
            showOffsetDialog(type, currentOffset)
        }

        // ── DND Toggle: save pref + schedule/cancel DND alarms ──
        adapter.onDNDToggleChanged = { type, enabled ->
            if (enabled) {
                if (!DNDController.hasDNDAccess(requireContext())) {
                    // Permission not granted — revert and show toast
                    PrayerAlarmPrefs.setDNDEnabled(type, false)
                    Toast.makeText(requireContext(), R.string.dnd_permission_snackbar, Toast.LENGTH_LONG).show()
                    adapter.notifyDataSetChanged()
                    Log.w(TAG, "DND toggle for $type denied — no DND access permission")
                } else if (PrayerAlarmPrefs.isAlarmEnabled(type) &&
                    hasAdhanDNDOverlap(type,
                        PrayerAlarmPrefs.getDNDOffset(type), PrayerAlarmPrefs.getDNDDuration(type))
                ) {
                    // Overlap detected — block DND enable, revert UI
                    Log.w(TAG, "DND enable blocked for $type — Adhan/DND overlap")
                    adapter.notifyDataSetChanged()
                } else {
                    PrayerAlarmPrefs.setDNDEnabled(type, true)
                    lastTimesMap?.let { timesMap ->
                        val dndEnabledTypes = PrayerType.entries.filter {
                            it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it)
                        }.toSet()
                        AlarmScheduler.scheduleDNDAlarms(requireContext(), timesMap, dndEnabledTypes)
                    }
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.dnd_toast_enabled, type.displayName),
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "DND enabled for $type — alarms scheduled")
                }
            } else {
                PrayerAlarmPrefs.setDNDEnabled(type, false)
                AlarmScheduler.cancelDNDAlarm(requireContext(), type)
                // If DND is currently active for this prayer, disable it immediately
                // so the phone restores its normal notification filter right away.
                PrayerDNDManager.cancelDND(requireContext())
                // Toast.makeText(requireContext(), R.string.dnd_alarm_cancelled_snackbar, Toast.LENGTH_SHORT).show()
                Toast.makeText(requireContext(),
                    getString(R.string.dnd_toast_disabled, type.displayName),
                    Toast.LENGTH_SHORT).show()
                Log.d(TAG, "DND disabled for $type — alarms cancelled, DND state cleared")
            }
        }

        // ── DND Offset Changed: validate overlap, save pref — onSharedPreferenceChanged handles reschedule ──
        adapter.onDNDOffsetChanged = { type, minutes ->
            if (PrayerAlarmPrefs.isDNDEnabled(type) && PrayerAlarmPrefs.isAlarmEnabled(type) &&
                hasAdhanDNDOverlap(type, minutes, PrayerAlarmPrefs.getDNDDuration(type))
            ) {
                Log.w(TAG, "DND offset change for $type blocked — Adhan/DND overlap")
                adapter.notifyDataSetChanged()
            } else {
                PrayerAlarmPrefs.setDNDOffset(type, minutes)
                Log.d(TAG, "DND offset for $type set to $minutes")
            }
        }

        // ── DND Duration Changed: validate overlap, save pref — onSharedPreferenceChanged handles reschedule ──
        adapter.onDNDDurationChanged = { type, minutes ->
            if (PrayerAlarmPrefs.isDNDEnabled(type) && PrayerAlarmPrefs.isAlarmEnabled(type) &&
                hasAdhanDNDOverlap(type, PrayerAlarmPrefs.getDNDOffset(type), minutes)
            ) {
                Log.w(TAG, "DND duration change for $type blocked — Adhan/DND overlap")
                adapter.notifyDataSetChanged()
            } else {
                PrayerAlarmPrefs.setDNDDuration(type, minutes)
                Log.d(TAG, "DND duration for $type set to $minutes")
            }
        }

        // ── Custom Duration Set: validate overlap, save raw value without clamping ──
        adapter.onCustomDurationSet = { type, minutes ->
            if (PrayerAlarmPrefs.isDNDEnabled(type) && PrayerAlarmPrefs.isAlarmEnabled(type) &&
                hasAdhanDNDOverlap(type, PrayerAlarmPrefs.getDNDOffset(type), minutes)
            ) {
                Log.w(TAG, "Custom DND duration for $type blocked — Adhan/DND overlap")
                adapter.notifyDataSetChanged()
            } else {
                PrayerAlarmPrefs.setDNDDurationRaw(type, minutes)
                Log.d(TAG, "Custom DND duration for $type set to $minutes")
            }
        }

        // ── DND Help Click: show help dialog ──
        adapter.onDndHelpClick = {
            showDndHelpDialog()
        }

        // ── Prayer Help Click: show per-prayer tooltip popup ──
        adapter.onPrayerHelpClick = { prayerType, anchorView ->
            val now = System.currentTimeMillis()
            if (prayerHelpPopup?.isShowing == true) {
                // Popup is showing — dismiss it (second-tap dismiss)
                val popup = prayerHelpPopup
                prayerHelpPopup = null
                popup?.dismiss()
                lastPopupDismissMs = now
            } else if (now - lastPopupDismissMs >= POPUP_DISMISS_DEBOUNCE_MS) {
                // Not recently dismissed — show new popup
                showPrayerHelpPopup(prayerType, anchorView)
            }
            // else: debounce — ignore outside-tap passthrough
        }

        binding.recyclerPrayerTimes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPrayerTimes.adapter = adapter

        // Location error card buttons (safe-call for nullable binding views)
        binding.btnSetLocationManually?.setOnClickListener {
            showManualLocationDialog()
        }
        binding.btnEnableLocation?.setOnClickListener {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        binding.btnLocationRetry?.setOnClickListener {
            LocationProvider.requestFreshLocation { lat, lng ->
                LocationProvider.cacheLocation(lat, lng)
                if (PrayerAlarmPrefs.isMasterAlarmEnabled()) {
                    lastTimesMap?.let { times ->
                        val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes().toSet()
                        AlarmScheduler.rescheduleAll(requireContext(), times, enabledTypes)
                        val dndEnabledTypes = PrayerType.entries.filter { it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it) }.toSet()
                        AlarmScheduler.cancelDNDAlarms(requireContext())
                        AlarmScheduler.scheduleDNDAlarms(requireContext(), times, dndEnabledTypes)
                    }
                }
                calculatePrayerTimes()
            }
        }

        calculatePrayerTimes()
    }

    override fun onResume() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_RESUME", mapOf("fragment" to "PrayerTimesFragment"))
        super.onResume()
        // Register listener so preferences changes trigger recalculation
        MosqueDNDApp.prefs.registerOnSharedPreferenceChangeListener(this)
        // Reschedule alarms if location or timezone changed while fragment was not resumed
        rescheduleIfLocationOrTimezoneChanged()
        // ── Day-change detection: handles force-stop + reopen on a different calendar day ──
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (today != lastSeenDayOfYear) {
            Log.d(TAG, "Day change detected: lastSeenDayOfYear=$lastSeenDayOfYear, today=$today — recalculating + rescheduling")
            calculatePrayerTimes()
            // rescheduleIfLocationOrTimezoneChanged() already handles location/timezone rescheduling;
            // re-check in case all three changed simultaneously (no harm calling — day guard prevents re-entry)
            rescheduleIfLocationOrTimezoneChanged()
            lastSeenDayOfYear = today
        }
        // Show first-launch sect selector if not yet shown (no day-change guard — always check)
        showFirstLaunchSectSelector()
    }

    override fun onPause() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_PAUSE", mapOf("fragment" to "PrayerTimesFragment"))
        super.onPause()
        MosqueDNDApp.prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.ACTION, "PREF_CHANGED", mapOf("key" to (key ?: "null")))
        if (key == "hijri_date_offset") {
            Log.d(TAG, "Hijri offset changed — updating Hijri date display")
            updateHijriDate()
        }

        if (key == PREF_CALCULATION_METHOD || key == PREF_ASR_METHOD
            || key == "pref_master_alarm_enabled"
            || PrayerAlarmPrefs.isOffsetKey(key)
            || PrayerAlarmPrefs.isAlarmEnabledKey(key)
            || PrayerAlarmPrefs.isDNDEnabledKey(key)
            || PrayerAlarmPrefs.isDNDOffsetKey(key)
            || PrayerAlarmPrefs.isDNDDurationKey(key)
        ) {
            Log.d(TAG, "Preference $key changed — recalculating prayer times (UI only)")

            // ── AUDIT FIX: Removed duplicate AlarmScheduler.rescheduleAll() calls here. ──
            // Scheduling is handled by the direct UI callbacks (onToggleChanged for alarm
            // toggles, offset dialog for offset changes, DND toggles for DND changes) and
            // by SettingsFragment.propagateSettingsChange() for calculation-method changes.
            // This listener only updates the display and widget to avoid double-firing
            // setExactAndAllowWhileIdle() on every SharedPreferences change.
            calculatePrayerTimes()

            // DND offset and duration changes still need DND alarm reschedule (no direct callback handles these)
            if (PrayerAlarmPrefs.isDNDOffsetKey(key) || PrayerAlarmPrefs.isDNDDurationKey(key)) {
                if (PrayerAlarmPrefs.isMasterAlarmEnabled()) {
                    lastTimesMap?.let { times ->
                        val dndEnabledTypes = PrayerType.entries.filter {
                            it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it)
                        }.toSet()
                        AlarmScheduler.cancelDNDAlarms(requireContext())
                        AlarmScheduler.scheduleDNDAlarms(requireContext(), times, dndEnabledTypes)
                    }
                }
            }

            triggerWidgetUpdate()
        }
    }

    /**
     * Calculate and display prayer times using PrayTimesCalculator.
     * Reads calculation method and Asr method from SharedPreferences.
     */
    fun calculatePrayerTimes() {
        val now = Calendar.getInstance()
        val (lat, lng) = LocationProvider.getEffectiveLocation()

        // Update location source indicator
        updateLocationSourceIndicator()

        // Show error card if no reliable location is available
        if (!LocationProvider.isLocationReliable()) {
            showLocationErrorUI()
            // Still calculate with effective location (may be Karachi default)
            // so the UI has data to display, even though it may be inaccurate
        } else {
            hideLocationErrorUI()
        }

        val prefs = MosqueDNDApp.prefs
        val calcMethodStr = prefs.getString(PREF_CALCULATION_METHOD, "mwl") ?: "mwl"
        val asrMethodStr = prefs.getString(PREF_ASR_METHOD, "standard") ?: "standard"

        val calcMethod = CalculationMethod.fromPreferenceValue(calcMethodStr)
        val asrMethod = AsrMethod.fromPreferenceValue(asrMethodStr)

        val offsets = PrayerAlarmPrefs.toOffsetsMap()
        Log.d(TAG, "Calculating prayer times with method=$calcMethod, asr=$asrMethod, offsets=$offsets")

        val timesMap = PrayTimesCalculator.calculate(
            date = now.time,
            latitude = lat,
            longitude = lng,
            method = calcMethod,
            asrMethod = asrMethod,
            offsets = offsets
        )
        lastTimesMap = timesMap

        // Maintain canonical prayer order
        val prayerOrder = PrayerType.entries.toList()
        val times = prayerOrder.map { timesMap[it]!! }

        // Find the next prayer (first prayer whose time is after now)
        var nextIndex = -1
        for (i in times.indices) {
            if (times[i].time.after(now)) {
                nextIndex = i
                break
            }
        }

        // If all prayers have passed, wrap to next day's first prayer
        if (nextIndex == -1) {
            nextIndex = 0
        }

        val updatedTimes = times.mapIndexed { index, prayerTime ->
            prayerTime.copy(isNext = index == nextIndex)
        }

        adapter.updateData(updatedTimes, nextIndex)

        // Update DND time range display for expanded card
        updateDndTimeRanges()

        // Populate landscape next-prayer summary panel (only exists in landscape layout)
        populateNextPrayerPanel(times, nextIndex, now)

        // Schedule the daily midnight alarm so prayer times refresh at day change
        DailyRecalculationHelper.scheduleNextMidnightAlarm(requireContext())

        // Show Hijri date
        updateHijriDate()
    }

    /**
     * Broadcasts an ACTION_APPWIDGET_UPDATE so the PrayerTimesWidget refreshes.
     */
    private fun triggerWidgetUpdate() {
        val context = requireContext()
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, PrayerTimesWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_container)
            val updateIntent = Intent(
                AppWidgetManager.ACTION_APPWIDGET_UPDATE,
                null,
                context,
                PrayerTimesWidget::class.java
            ).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(updateIntent)
            Log.d(TAG, "Widget update broadcast sent from fragment")
        }
    }

    /**
     * Refresh the Hijri date display. Delegates to [showHijriDate].
     * This is the method called when the hijri offset preference changes.
     */
    private fun updateHijriDate() {
        showHijriDate()
    }

    /**
     * Compute and display today's Hijri date in the hijri_date TextView.
     */
    private fun showHijriDate() {
        try {
            val hijriDate = IslamicCalendar.getCurrentHijriDate()
            val formatted = IslamicCalendar.format(hijriDate)
            val prefix = getString(R.string.hijri_date_prefix)
            binding.textHijriDate.text = "$prefix$formatted"
            binding.textHijriDate.visibility = View.VISIBLE
            Log.d(TAG, "Hijri date: $formatted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Hijri date: ${e.message}")
            binding.textHijriDate.visibility = View.GONE
        }
    }

    /**
     * Computes "DND: hh:mm - hh:mm" strings for all prayer types and pushes
     * them to the adapter so the expanded card's time range text updates.
     */
    private fun updateDndTimeRanges() {
        val timesMap = lastTimesMap ?: return
        val ranges = mutableMapOf<PrayerType, String>()
        for (type in PrayerType.entries) {
            if (type == PrayerType.SUNRISE) continue
            val prayerTime = timesMap[type]?.time ?: continue
            val alarmOffset = PrayerAlarmPrefs.getOffset(type)
            val rawMs = prayerTime.timeInMillis - (alarmOffset * 60 * 1000)
            val offset = PrayerAlarmPrefs.getDNDOffset(type)
            val duration = PrayerAlarmPrefs.getDNDDuration(type)

            val startCal = Calendar.getInstance().apply { timeInMillis = rawMs }
            startCal.add(Calendar.MINUTE, offset)

            val endCal = startCal.clone() as Calendar
            endCal.add(Calendar.MINUTE, duration)

            val startStr = formatDndTime(startCal)
            val endStr = formatDndTime(endCal)
            ranges[type] = getString(R.string.dnd_time_range, startStr, endStr)
        }
        adapter.dndTimeRanges = ranges
        // If a card is expanded, force a rebind so the time range text updates
        if (adapter.expandedPosition != null) {
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * Formats a Calendar time as "HH:MM" for DND time range display.
     */
    private fun formatDndTime(cal: Calendar): String {
        return String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    // ──────────────────────────────────────────────
    // Adhan/DND Overlap Validation
    // ──────────────────────────────────────────────

    /**
     * Shows the overlap Toast at most once per [OVERLAP_TOAST_DEBOUNCE_MS] milliseconds.
     */
    private fun showOverlapToastDebounced() {
        val now = System.currentTimeMillis()
        if (now - lastOverlapToastTime >= OVERLAP_TOAST_DEBOUNCE_MS) {
            lastOverlapToastTime = now
            Toast.makeText(requireContext(), R.string.adhan_dnd_overlap, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Checks whether the Adhan alarm trigger time falls within the DND interval
     * for the given prayer type, using the given offset/duration parameters.
     *
     * The Adhan trigger from [lastTimesMap] is baked (alarm offset already applied
     * by PrayTimesCalculator). The DND interval starts from the *raw* (unbaked)
     * prayer time plus the DND offset:
     *   [prayerRaw + dndOffset, prayerRaw + dndOffset + dndDuration)
     *
     * If overlap is detected, shows a debounced Toast and calls
     * [adapter.notifyDataSetChanged] to revert the UI controls.
     *
     * @return true if overlap detected (change should be blocked), false otherwise
     */
    private fun hasAdhanDNDOverlap(
        prayerType: PrayerType,
        dndOffsetMinutes: Int,
        dndDurationMinutes: Int
    ): Boolean {
        val prayerTimeCal = lastTimesMap?.get(prayerType)?.time ?: return false
        val adhanMs = prayerTimeCal.timeInMillis
        // Unbake alarm offset: lastTimesMap already includes the current alarm offset
        val rawMs = adhanMs - TimeUnit.MINUTES.toMillis(PrayerAlarmPrefs.getOffset(prayerType).toLong())
        val dndStartMs = rawMs + TimeUnit.MINUTES.toMillis(dndOffsetMinutes.toLong())
        val dndEndMs = dndStartMs + TimeUnit.MINUTES.toMillis(dndDurationMinutes.toLong())

        val overlap = adhanMs >= dndStartMs && adhanMs < dndEndMs
        if (overlap) {
            showOverlapToastDebounced()
            adapter.notifyDataSetChanged()
        }
        return overlap
    }

    /**
     * Shows a MaterialAlertDialog with a SeekBar to adjust the offset (-60..+60 min)
     * for the given prayer type. Dialog title shows the prayer name and current time.
     * The adjusted prayer time updates in real time as the user drags the SeekBar.
     */
    private fun showOffsetDialog(prayerType: PrayerType, currentOffset: Int = PrayerAlarmPrefs.getOffset(prayerType)) {
        val formattedTime = lastTimesMap?.get(prayerType)?.formattedTime ?: ""
        val title = "${prayerType.displayName} ($formattedTime)"

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 24)
        }

        val offsetText = TextView(requireContext()).apply {
            text = formatOffset(currentOffset)
            textSize = 32f
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(offsetText)

        val adjustedTimeText = TextView(requireContext()).apply {
            text = getString(R.string.offset_adjusted_time, computeAdjustedTime(prayerType, currentOffset))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        layout.addView(adjustedTimeText)

        val seekBar = SeekBar(requireContext()).apply {
            max = 120
            progress = currentOffset + 60
        }
        layout.addView(seekBar)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val offset = progress - 60
                offsetText.text = formatOffset(offset)
                adjustedTimeText.text = getString(R.string.offset_adjusted_time, computeAdjustedTime(prayerType, offset))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val offset = seekBar.progress - 60
                // Validate Adhan/DND overlap before saving the new offset
                val newAlarmOverlap = PrayerAlarmPrefs.isAlarmEnabled(prayerType) && PrayerAlarmPrefs.isDNDEnabled(prayerType)
                if (newAlarmOverlap) {
                    // Compute the new Adhan trigger time using the proposed offset
                    val bakedMs = lastTimesMap?.get(prayerType)?.time?.timeInMillis
                    if (bakedMs != null) {
                        val oldOffset = PrayerAlarmPrefs.getOffset(prayerType)
                        val unbakedMs = bakedMs - TimeUnit.MINUTES.toMillis(oldOffset.toLong())
                        val newAdhanMs = unbakedMs + TimeUnit.MINUTES.toMillis(offset.toLong())

                        val dndOffset = PrayerAlarmPrefs.getDNDOffset(prayerType)
                        val dndDuration = PrayerAlarmPrefs.getDNDDuration(prayerType)
                        // Compute DND window from the unbaked raw prayer time so alarm offset isn't double-counted
                        val dndStartMs = unbakedMs + TimeUnit.MINUTES.toMillis(dndOffset.toLong())
                        val dndEndMs = dndStartMs + TimeUnit.MINUTES.toMillis(dndDuration.toLong())

                        if (newAdhanMs >= dndStartMs && newAdhanMs < dndEndMs) {
                            showOverlapToastDebounced()
                            Log.w(TAG, "Alarm offset change for $prayerType blocked — Adhan/DND overlap")
                            return@setPositiveButton
                        }
                    }
                }
                PrayerAlarmPrefs.setOffset(prayerType, offset)
                calculatePrayerTimes()
                triggerWidgetUpdate()
                // Reschedule alarms only if master alarm is enabled
                if (PrayerAlarmPrefs.isMasterAlarmEnabled()) {
                    lastTimesMap?.let { times ->
                        val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes().toSet()
                        AlarmScheduler.rescheduleAll(requireContext(), times, enabledTypes)
                        // Also reschedule DND alarms to keep in sync
                        val dndEnabledTypes = PrayerType.entries.filter { it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it) }.toSet()
                        AlarmScheduler.cancelDNDAlarms(requireContext())
                        AlarmScheduler.scheduleDNDAlarms(requireContext(), times, dndEnabledTypes)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Computes the adjusted prayer time string given a prayer type and an offset in minutes.
     * Handles midnight rollover (next day / previous day indicators).
     * Returns a string like "12:45" or "00:30 (next day)" or "23:45 (previous day)".
     */
    private fun computeAdjustedTime(prayerType: PrayerType, offsetMinutes: Int): String {
        val bakedTime = lastTimesMap?.get(prayerType)?.time ?: return ""
        val storedOffset = PrayerAlarmPrefs.getOffset(prayerType)
        // Subtract baked-in offset to get raw time, then apply preview offset
        val rawHours = bakedTime.get(Calendar.HOUR_OF_DAY)
        val rawMinutes = bakedTime.get(Calendar.MINUTE)
        val rawTotalMinutes = rawHours * 60 + rawMinutes - storedOffset

        val totalMinutes = rawTotalMinutes + offsetMinutes
        val dayIndicator = when {
            totalMinutes < 0 -> " (${getString(R.string.offset_previous_day)})"
            totalMinutes >= 1440 -> " (${getString(R.string.offset_next_day)})"
            else -> ""
        }
        val wrappedMinutes = ((totalMinutes % 1440) + 1440) % 1440
        val adjustedHours = wrappedMinutes / 60
        val adjustedMins = wrappedMinutes % 60

        return "${String.format("%02d:%02d", adjustedHours, adjustedMins)}$dayIndicator"
    }

    /**
     * Populates the landscape next-prayer summary panel with the next prayer name,
     * time, and a countdown. The views are only present in the landscape layout
     * variant, so all accesses use safe-call (?.) to handle portrait mode gracefully.
     */
    private fun populateNextPrayerPanel(
        times: List<PrayerTime>,
        nextIndex: Int,
        now: Calendar
    ) {
        val nextPrayer = times[nextIndex]
        binding.textNextPrayerName?.text = nextPrayer.type.displayName
        binding.textNextPrayerTime?.text = nextPrayer.formattedTime

        // Compute countdown from current time to next prayer time
        val diffMs = nextPrayer.time.timeInMillis - now.timeInMillis
        val countdownText = if (diffMs > 0) {
            val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60
            if (hours > 0) "${hours}h ${minutes}m remaining"
            else "${minutes}m remaining"
        } else {
            // Next prayer is tomorrow (all today's prayers have passed)
            getString(R.string.widget_next_prayer)
        }
        binding.textNextPrayerCountdown?.text = countdownText
        binding.layoutNextPrayerSummary?.visibility = View.VISIBLE
    }

    /**
     * Shows the location error card and a Toast.
     */
    private fun showLocationErrorUI() {
        binding.cardLocationError?.visibility = View.VISIBLE

        // Show Toast for location unavailable
        Toast.makeText(
            requireContext(),
            R.string.location_unavailable_title,
            Toast.LENGTH_LONG
        ).show()

        Log.d(TAG, "Location unavailable — showing error UI")
    }

    /**
     * Hides the location error card.
     */
    private fun hideLocationErrorUI() {
        binding.cardLocationError?.visibility = View.GONE
    }

    /**
     * Updates the location source indicator text to show GPS or Manual.
     */
    private fun updateLocationSourceIndicator() {
        val sourceView = binding.textLocationSource
        if (sourceView == null) return

        if (LocationProvider.hasLocationOverride()) {
            val (lat, lng) = LocationProvider.getEffectiveLocation()
            sourceView.text = getString(
                R.string.location_source_manual_location_info,
                "%.4f".format(lat),
                "%.4f".format(lng)
            )
            sourceView.visibility = View.VISIBLE
            // Click to clear override
            sourceView.setOnClickListener {
                LocationProvider.clearLocationOverride()
                if (PrayerAlarmPrefs.isMasterAlarmEnabled()) {
                    lastTimesMap?.let { times ->
                        val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes().toSet()
                        AlarmScheduler.rescheduleAll(requireContext(), times, enabledTypes)
                        val dndEnabledTypes = PrayerType.entries.filter { it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it) }.toSet()
                        AlarmScheduler.cancelDNDAlarms(requireContext())
                        AlarmScheduler.scheduleDNDAlarms(requireContext(), times, dndEnabledTypes)
                    }
                }
                calculatePrayerTimes()
                Toast.makeText(requireContext(), R.string.location_source_gps, Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, "Location source: Manual (override active)")
        } else if (MosqueDNDApp.prefs.getBoolean("has_cached_location", false)) {
            sourceView.text = getString(R.string.location_source_gps)
            sourceView.visibility = View.VISIBLE
            sourceView.setOnClickListener(null)
            Log.d(TAG, "Location source: GPS (cached)")
        } else {
            sourceView.visibility = View.GONE
            sourceView.setOnClickListener(null)
        }
    }

    /**
     * Shows a dialog with latitude and longitude fields for manual location entry.
     * Validates that lat is in [-90, 90] and lng is in [-180, 180].
     */
    private fun showManualLocationDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val latInput = EditText(requireContext()).apply {
            hint = getString(R.string.location_manual_latitude_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        layout.addView(latInput)

        val lngInput = EditText(requireContext()).apply {
            hint = getString(R.string.location_manual_longitude_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
        }
        layout.addView(lngInput)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.location_manual_title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val latStr = latInput.text.toString().trim()
                val lngStr = lngInput.text.toString().trim()

                if (latStr.isEmpty() || lngStr.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.location_invalid_input, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val lat = latStr.toDoubleOrNull()
                val lng = lngStr.toDoubleOrNull()

                if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                    Toast.makeText(requireContext(), R.string.location_invalid_input, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Set the override and recalculate immediately
                LocationProvider.setLocationOverride(lat, lng)
                Log.d(TAG, "Manual location set: $lat, $lng")
                if (PrayerAlarmPrefs.isMasterAlarmEnabled()) {
                    lastTimesMap?.let { times ->
                        val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes().toSet()
                        AlarmScheduler.rescheduleAll(requireContext(), times, enabledTypes)
                        val dndEnabledTypes = PrayerType.entries.filter { it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it) }.toSet()
                        AlarmScheduler.cancelDNDAlarms(requireContext())
                        AlarmScheduler.scheduleDNDAlarms(requireContext(), times, dndEnabledTypes)
                    }
                }
                calculatePrayerTimes()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        // Show keyboard on dialog open (delay to let dialog render)
        latInput.postDelayed({
            latInput.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(latInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    /**
     * Shows a MaterialAlertDialog explaining the Silent (DND) Mode feature.
     */
    private fun showDndHelpDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dnd_help_title)
            .setMessage(R.string.dnd_help_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * Shows a per-prayer help tooltip as a PopupWindow with gold-accent styling,
     * fade animation, outside-tap dismiss, and right-edge boundary detection.
     */
    private fun showPrayerHelpPopup(prayerType: PrayerType, anchorView: View) {
        val helpText = getPrayerHelpText(prayerType)
        if (helpText.isEmpty()) return

        // Build popup content view with gold-accent card styling
        val textView = TextView(requireContext()).apply {
            text = helpText
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(28, 18, 28, 18)
            // Gold-accented rounded rectangle background matching DND help dialog style
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(ContextCompat.getColor(context, R.color.card_default))
                setStroke(2, ContextCompat.getColor(context, R.color.gold_primary))
                cornerRadius = 12f
            }
            background = bg
        }

        val popup = PopupWindow(
            textView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            animationStyle = android.R.style.Animation_Dialog
            setOnDismissListener {
                prayerHelpPopup = null
                lastPopupDismissMs = System.currentTimeMillis()
            }
        }

        // Boundary detection: reposition if popup would overflow right edge
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = textView.measuredWidth
        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        val anchorX = anchorLocation[0]
        val anchorWidth = anchorView.width
        val screenWidth = resources.displayMetrics.widthPixels
        val margin = resources.getDimensionPixelSize(R.dimen.spacing_sm)

        if (anchorX + popupWidth > screenWidth - margin) {
            // Right-align popup with anchor's right edge
            val xOffset = -(popupWidth - anchorWidth)
            val clampedOffset = maxOf(xOffset, -(anchorX - margin))
            popup.showAsDropDown(anchorView, clampedOffset, 0)
        } else {
            popup.showAsDropDown(anchorView, 0, 0)
        }

        prayerHelpPopup = popup
    }

    /**
     * Returns the per-prayer help text from strings.xml for the given prayer type.
     */
    private fun getPrayerHelpText(prayerType: PrayerType): String {
        return when (prayerType) {
            PrayerType.FAJR -> getString(R.string.prayer_help_fajr)
            PrayerType.DHUHR -> getString(R.string.prayer_help_dhuhr)
            PrayerType.ASR -> getString(R.string.prayer_help_asr)
            PrayerType.MAGHRIB -> getString(R.string.prayer_help_maghrib)
            PrayerType.ISHA -> getString(R.string.prayer_help_isha)
            else -> ""
        }
    }

    /**
     * Detects whether location, timezone, or calendar day changed while the fragment
     * was not resumed (e.g. fresh GPS fix obtained via MapActivity, user changed system
     * timezone, or app was force-stopped and reopened on a different day).
     * Uses a stored version counter (location), timezone ID, and day-of-year field to
     * avoid redundant reschedules. Calls [AlarmScheduler.rescheduleAll] at most once per
     * resume cycle.
     */
    private fun rescheduleIfLocationOrTimezoneChanged() {
        val prefs = MosqueDNDApp.prefs
        val context = requireContext()

        // ── Location version check ──
        val savedLocationVersion = prefs.getInt(PREF_LAST_LOCATION_VERSION, -1)
        val currentLocationVersion = LocationProvider.getLocationVersion()
        val locationChanged = currentLocationVersion != savedLocationVersion

        // ── Timezone ID check ──
        val savedTimezone = prefs.getString(PREF_LAST_TIMEZONE_ID, null)
        val currentTimezone = LocationProvider.getLastKnownTimezoneId()
        val timezoneChanged = savedTimezone != null && savedTimezone != currentTimezone

        // ── Day-change detection (handles force-stop + reopen) ──
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val dayChanged = today != lastSeenDayOfYear
        if (dayChanged) {
            lastSeenDayOfYear = today
        }

        if (locationChanged || timezoneChanged || dayChanged) {
            Log.d(TAG, "Triggering reschedule: locationChanged=$locationChanged, timezoneChanged=$timezoneChanged, dayChanged=$dayChanged")

            // Update stored snapshots so we don't reschedule again until next real change
            prefs.edit()
                .putInt(PREF_LAST_LOCATION_VERSION, currentLocationVersion)
                .putString(PREF_LAST_TIMEZONE_ID, currentTimezone)
                .apply()

            // Only reschedule if master alarm is enabled
            if (PrayerAlarmPrefs.isMasterAlarmEnabled()) {
                lastTimesMap?.let { times ->
                    val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes().toSet()
                    AlarmScheduler.rescheduleAll(context, times, enabledTypes)
                    val dndEnabledTypes = PrayerType.entries.filter {
                        it != PrayerType.SUNRISE && PrayerAlarmPrefs.isDNDEnabled(it)
                    }.toSet()
                    AlarmScheduler.cancelDNDAlarms(context)
                    AlarmScheduler.scheduleDNDAlarms(context, times, dndEnabledTypes)
                }
            }
        }
    }

    override fun onDestroyView() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_DESTROY_VIEW", mapOf("fragment" to "PrayerTimesFragment"))
        super.onDestroyView()
        // Dismiss any showing popup to avoid window leaks
        prayerHelpPopup?.dismiss()
        prayerHelpPopup = null
        _binding = null
        AlarmScheduler.setAlarmScheduleErrorCallback(null)
    }

    override fun onDestroy() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "FRAGMENT_DESTROY", mapOf("fragment" to "PrayerTimesFragment"))
        super.onDestroy()
    }

    /**
     * Shows a one-time school selector dialog when the user first opens the
     * Prayer Times tab and [PREF_SECT] is not yet stored.
     * Displays all schools from sect_entries as a single-select list.
     */
    private fun showFirstLaunchSectSelector() {
        val prefs = MosqueDNDApp.prefs
        if (prefs.contains(PREF_SECT)) {
            Log.d(TAG, "School already selected — skipping first-launch dialog")
            return
        }

        val schoolNames = resources.getStringArray(R.array.sect_entries)
        val schoolValues = resources.getStringArray(R.array.sect_values)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sect_selector_title)
            .setItems(schoolNames) { _, which ->
                val sect = schoolValues[which]
                prefs.edit().putString(PREF_SECT, sect).apply()
                applySectFromFragment(sect)
                Log.d(TAG, "First-launch: $sect selected")
                calculatePrayerTimes()
            }
            .setCancelable(true)
            .setOnCancelListener {
                // User dismissed without choosing — default to Shafi'i
                prefs.edit().putString(PREF_SECT, "shafii").apply()
                applySectFromFragment("shafii")
                Log.d(TAG, "First-launch dialog cancelled — defaulting to Shafi'i")
                calculatePrayerTimes()
            }
            .show()
    }

    /**
     * Applies the school mapping from within the fragment (same logic as SettingsFragment).
     *
     * - Hanafi: sets Asr=HANAFI, preserves calc method
     * - Hanbali/Maliki/Shafi'i/Zaydi/Ibadi: sets Asr=SHAFII, preserves calc method
     * - Ja'fari: sets Calc=JAFARI + Asr=SHAFII
     * - Custom: leaves both unchanged
     */
    private fun applySectFromFragment(sect: String) {
        val prefs = MosqueDNDApp.prefs

        when (sect) {
            "hanafi" -> {
                prefs.edit().putString(PREF_ASR_METHOD, "hanafi").apply()
                Log.d(TAG, "applySectFromFragment: Hanafi — Asr set to HANAFI, calc preserved")
            }
            "hanbali", "maliki", "shafii", "zaydi", "ibadi" -> {
                prefs.edit().putString(PREF_ASR_METHOD, "standard").apply()
                Log.d(TAG, "applySectFromFragment: $sect — Asr set to SHAFII, calc preserved")
            }
            "jafari" -> {
                prefs.edit()
                    .putString(PREF_CALCULATION_METHOD, "jafari")
                    .putString(PREF_ASR_METHOD, "standard")
                    .apply()
                Log.d(TAG, "applySectFromFragment: Jafari — calc set to JAFARI, Asr set to SHAFII")
            }
            "custom" -> {
                Log.d(TAG, "applySectFromFragment: Custom — calc and Asr unchanged")
            }
            else -> {
                Log.w(TAG, "applySectFromFragment: Unknown school: $sect — no changes")
            }
        }
    }
}
