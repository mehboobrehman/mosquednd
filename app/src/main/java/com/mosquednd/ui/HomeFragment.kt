package com.mosquednd.ui

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mosquednd.MosqueDNDApp
import com.mosquednd.R
import com.mosquednd.databinding.FragmentHomeBinding
import com.mosquednd.service.LocationMonitorService
import com.mosquednd.util.DNDController
import com.mosquednd.geofence.GeofenceConfig
import com.mosquednd.util.BootPrefs
import com.mosquednd.util.LocationProvider
import androidx.lifecycle.lifecycleScope
import com.mosquednd.ui.BackgroundLocationRationaleFragment
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(
            BackgroundLocationRationaleFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            when (bundle.getString(BackgroundLocationRationaleFragment.KEY_ACTION)) {
                BackgroundLocationRationaleFragment.ACTION_GRANT -> {
                    Log.d(TAG, "Rationale granted — launching background location permission")
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                BackgroundLocationRationaleFragment.ACTION_DISMISS -> {
                    Log.d(TAG, "Rationale dismissed")
                    MosqueDNDApp.prefs.edit().putBoolean(PREF_MONITORING_ENABLED, false).apply()
                    updateMonitorUI()
                }
            }
        }
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var isMonitoringToggleProcessing = false
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "LocationMonitorService connected — refreshing UI")
            updateMonitorUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "LocationMonitorService disconnected (crashed or killed by OS)")
            isBound = false
            MosqueDNDApp.prefs.edit().putBoolean(PREF_MONITORING_ENABLED, false).apply()
            updateMonitorUI()
        }
    }

    companion object {
        private const val TAG = "HomeFragment"
        private const val PREF_MONITORING_ENABLED = "pref_monitoring_enabled"
        private const val PREF_MONITORING_INTERVAL = "pref_monitoring_interval"
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            LocationProvider.requestFreshLocation(requireContext()) { lat, lng ->
                Log.d(TAG, "Permissions-granted location cached: $lat, $lng")
            }
            startMonitoringFlow()
} else {
            Log.w(TAG, "Location permission denied — monitoring cannot start")
            Toast.makeText(requireContext(), getString(R.string.map_location_permission_required), Toast.LENGTH_LONG).show()
            revertMonitoringState()
            updateMonitorUI()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Background location permission granted")
            startMonitoring()
        } else {
            Log.w(TAG, "Background location permission denied")
            MosqueDNDApp.prefs.edit().putBoolean(PREF_MONITORING_ENABLED, false).apply()
            if (!shouldShowRequestPermissionRationale(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            ) {
                showBackgroundLocationSettingsDialog()
} else {
                Toast.makeText(
                    requireContext(),
                    R.string.background_location_required,
                    Toast.LENGTH_LONG
                ).show()
            }
            updateMonitorUI()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateDNDCard()
        updateMonitorUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unbindFromMonitorService()
        _binding = null
    }

    private fun setupUI() {
        binding.btnGrantDnd.setOnClickListener {
            DNDController.requestDNDAccess(requireContext())
        }

        setupMonitoringToggle()
        setupMonitoringHelp()

        val intervals = arrayOf("15 minutes", "30 minutes", "1 hour", "2 hours")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInterval.adapter = adapter
        binding.spinnerInterval.setSelection(1)

        setupRadiusUI()
    }

    private fun updateDNDCard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.cardDndPermission.visibility = if (DNDController.hasDNDAccess(requireContext())) View.GONE else View.VISIBLE
        }
    }

    private fun setupMonitoringToggle() {
        binding.layoutMonitorToggle.setOnClickListener {
            performToggleWithHaptic()
        }
    }

    private fun setupMonitoringHelp() {
        binding.imageMonitorHelp.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.help_location_title)
                .setMessage(R.string.help_location_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun performToggleWithHaptic() {
        if (isMonitoringToggleProcessing) {
            Log.d(TAG, "Toggle already processing — ignoring rapid tap")
            return
        }
        isMonitoringToggleProcessing = true
        binding.switchMonitor.isEnabled = false

        try {
            val wasRunning = LocationMonitorService.isServiceRunning()
            if (wasRunning) {
                binding.textMonitorLabel.text = getString(R.string.monitoring_status_off)
                binding.textMonitorLabel.setTextColor(resources.getColor(R.color.text_primary, null))
                binding.switchMonitor.isChecked = false
                binding.layoutInterval.visibility = View.VISIBLE

                stopMonitoring()
            } else {
                binding.textMonitorLabel.text = getString(R.string.monitoring_status_on)
                binding.textMonitorLabel.setTextColor(resources.getColor(R.color.gold_primary, null))
                binding.switchMonitor.isChecked = true
                binding.layoutInterval.visibility = View.GONE

                startMonitoringFlow()
            }
        } finally {
            isMonitoringToggleProcessing = false
            binding.switchMonitor.isEnabled = true
        }
    }

    private fun stopMonitoring() {
        try {
            unbindFromMonitorService()
            LocationMonitorService.stopService(requireContext())
            MosqueDNDApp.prefs.edit().putBoolean(PREF_MONITORING_ENABLED, false).apply()
            BootPrefs.setMonitoringEnabled(requireContext(), false)
            Toast.makeText(requireContext(), getString(R.string.monitoring_stopped), Toast.LENGTH_SHORT).show()
            provideHapticFeedback()
} catch (e: SecurityException) {
            Log.e(TAG, "SecurityException stopping monitoring", e)
            showMonitoringError(getString(R.string.monitoring_error_stop, "permission denied"))
            updateMonitorUI()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException stopping monitoring", e)
            showMonitoringError(getString(R.string.monitoring_error_stop, e.message))
            updateMonitorUI()
        }
    }

    private fun provideHapticFeedback() {
        val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.view.HapticFeedbackConstants.CONFIRM
        } else {
            android.view.HapticFeedbackConstants.LONG_PRESS
        }
        binding.layoutMonitorToggle.performHapticFeedback(feedbackConstant)
    }

    private fun startMonitoringFlow() {
        if (!DNDController.hasDNDAccess(requireContext())) {
            Toast.makeText(requireContext(), R.string.grant_dnd_access, Toast.LENGTH_SHORT).show()
            DNDController.requestDNDAccess(requireContext())
            updateMonitorUI()
            return
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), getString(R.string.map_location_permission_required), Toast.LENGTH_SHORT).show()
            requestLocationForMonitoring()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBackgroundLocation()
                return
            }
        }

        startMonitoring()
    }

    private fun startMonitoring() {
        checkSurvivalPermissions()

        val intervalMinutes = when (binding.spinnerInterval.selectedItemPosition) {
            0 -> 15
            1 -> 30
            2 -> 60
            3 -> 120
            else -> 30
        }
        try {
            LocationMonitorService.startService(requireContext(), intervalMinutes)
            bindToMonitorService()
            MosqueDNDApp.prefs.edit().putBoolean(PREF_MONITORING_ENABLED, true).apply()
            Toast.makeText(requireContext(), getString(R.string.monitoring_started), Toast.LENGTH_SHORT).show()
            provideHapticFeedback()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting monitoring", e)
            showMonitoringError(getString(R.string.monitoring_error_start, "permission denied"))
            revertMonitoringState()
            updateMonitorUI()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException starting monitoring", e)
            showMonitoringError(getString(R.string.monitoring_error_start, e.message))
            revertMonitoringState()
            updateMonitorUI()
        }
    }

    private fun requestLocationForMonitoring() {
        locationPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun requestBackgroundLocation() {
        if (shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        ) {
            BackgroundLocationRationaleFragment.show(this)
        } else {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun showBackgroundLocationSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.background_location_required)
            .setMessage(
                getString(R.string.help_location_message)
            )
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMonitorUI() {
        val running = LocationMonitorService.isServiceRunning()
        binding.textMonitorLabel.text = if (running) getString(R.string.monitoring_status_on) else getString(R.string.monitoring_status_off)
        binding.textMonitorLabel.setTextColor(resources.getColor(
            if (running) R.color.gold_primary else R.color.text_primary, null
        ))
        binding.switchMonitor.isChecked = running
        binding.layoutInterval.visibility = if (running) View.GONE else View.VISIBLE
        binding.layoutMonitorToggle.contentDescription = getString(R.string.monitor_toggle_desc)
    }

    private fun bindToMonitorService() {
        if (!isBound) {
            try {
                val intent = Intent(requireContext(), LocationMonitorService::class.java)
                requireContext().bindService(intent, serviceConnection, 0)
                isBound = true
                Log.d(TAG, "Bound to LocationMonitorService")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException binding to service", e)
            }
        }
    }

    private fun unbindFromMonitorService() {
        if (isBound) {
            try {
                requireContext().unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Error unbinding from LocationMonitorService", e)
            }
            isBound = false
            Log.d(TAG, "Unbound from LocationMonitorService")
        }
    }

    private fun showMonitoringError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun revertMonitoringState() {
        MosqueDNDApp.prefs.edit().putBoolean(PREF_MONITORING_ENABLED, false).apply()
    }

    private fun checkSurvivalPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = requireContext().getSystemService(PowerManager::class.java)
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)) {
                Log.d(TAG, "Battery optimization not disabled — showing dialog")
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.battery_optimization_title)
                    .setMessage(R.string.battery_optimization_message)
                    .setPositiveButton(R.string.battery_optimization_open_settings) { _, _ ->
                        Log.d(TAG, "Opening battery optimization settings")
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${requireContext().packageName}")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open battery optimization settings", e)
                        }
                    }
                    .setNegativeButton(R.string.battery_optimization_not_now) { _, _ ->
                        Log.w(TAG, "User declined battery optimization bypass")
                    }
                    .show()
            } else {
                Log.d(TAG, "Battery optimization already disabled")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val appOpsManager = requireContext().getSystemService(AppOpsManager::class.java)
            if (appOpsManager != null) {
                val mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    requireContext().packageName
                )
                if (mode != AppOpsManager.MODE_ALLOWED) {
                    Log.d(TAG, "Usage stats access not granted (mode=$mode) — showing dialog")
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.usage_access_title)
                        .setMessage(R.string.usage_access_message)
                        .setPositiveButton(R.string.usage_access_grant) { _, _ ->
                            Log.d(TAG, "Opening usage access settings")
                            try {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to open usage access settings", e)
                            }
                        }
                        .setNegativeButton(R.string.usage_access_skip) { _, _ ->
                            Log.w(TAG, "User skipped usage access grant")
                        }
                        .show()
                } else {
                    Log.d(TAG, "Usage stats access already granted")
                }
            }
        }
    }

    private fun setupRadiusUI() {
        val savedRadius = MosqueDNDApp.prefs.getInt("pref_detection_radius_m", GeofenceConfig.DEFAULT_DETECTION_RADIUS_M).coerceIn(10, 500)
        binding.seekbarRadius.progress = savedRadius - 10
        binding.labelRadiusValue.text = getString(R.string.radius_meters, savedRadius)

        binding.seekbarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress + 10
                binding.labelRadiusValue.text = getString(R.string.radius_meters, value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) { }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val value = binding.seekbarRadius.progress + 10
                MosqueDNDApp.prefs.edit().putInt("pref_detection_radius_m", value).apply()
            }
        })
    }
}
