package com.mosquednd.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mosquednd.MosqueDNDApp
import com.mosquednd.R
import com.mosquednd.calendar.IslamicCalendar
import com.mosquednd.databinding.ActivityMainBinding
import com.mosquednd.service.LocationMonitorService
import com.mosquednd.ui.onboarding.OnboardingActivity
import com.mosquednd.ui.BackgroundLocationRationaleFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.mosquednd.ui.settings.SettingsFragment
import com.mosquednd.ui.settings.SettingsActivity
import androidx.viewpager2.widget.ViewPager2
import com.mosquednd.util.LogHelper
import com.mosquednd.whatsnew.WhatsNewBottomSheetFragment
import com.mosquednd.whatsnew.WhatsNewManager

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREF_MONITORING_ENABLED = "pref_monitoring_enabled"
        private const val PREF_MONITORING_INTERVAL = "pref_monitoring_interval"
        const val EXTRA_NAVIGATE_TO = "navigate_to_fragment"
        private const val PREF_PENDING_NAVIGATE_TO_PRAYER_TIMES = "pending_navigate_to_prayer_times"
    }

    private var pendingUsageAccessGrant = false
    private var pendingNavigateToPrayerTimes = false

    private lateinit var binding: ActivityMainBinding

    private val hijriPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "hijri_date_offset") {
            updateHijriSubtitle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_CREATE", mapOf("activity" to "MainActivity"))
        applyTheme()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if ("prayer_times" == intent.getStringExtra(EXTRA_NAVIGATE_TO)) {
            pendingNavigateToPrayerTimes = true
        }

        setupUI()

        // First-launch onboarding check — after UI is set up so home screen
        // is always ready underneath. OnboardingActivity.finishOnboarding()
        // handles starting MainActivity; do NOT call finish() here to avoid
        // the task becoming empty and Android sending the user to home.
        if (!MosqueDNDApp.prefs.getBoolean("onboarding_seen", false)) {
            Log.d(TAG, "First launch detected — starting OnboardingActivity")
            if (pendingNavigateToPrayerTimes) {
                MosqueDNDApp.prefs.edit()
                    .putBoolean(PREF_PENDING_NAVIGATE_TO_PRAYER_TIMES, true)
                    .apply()
            }
            OnboardingActivity.start(this)
            return
        }

        // Restore any pending navigation that survived the onboarding recreation
        if (MosqueDNDApp.prefs.getBoolean(PREF_PENDING_NAVIGATE_TO_PRAYER_TIMES, false)) {
            pendingNavigateToPrayerTimes = true
            MosqueDNDApp.prefs.edit().remove(PREF_PENDING_NAVIGATE_TO_PRAYER_TIMES).apply()
        }

        // Normal startup — all permissions already processed
        setupAndShowUI()
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun applyTheme() {
        val theme = MosqueDNDApp.prefs.getString(MosqueDNDApp.PREFS_THEME, MosqueDNDApp.THEME_SYSTEM)
        val mode = when (theme) {
            MosqueDNDApp.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MosqueDNDApp.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "MosqueDND"
        invalidateOptionsMenu()

        binding.viewPager.adapter = MosquesPagerAdapter(this)

        setupBottomNavigation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navigateTo = intent.getStringExtra(EXTRA_NAVIGATE_TO)
        when (navigateTo) {
            "home" -> binding.bottomNavigation.selectedItemId = R.id.nav_home
            "mosques" -> binding.bottomNavigation.selectedItemId = R.id.nav_mosques
            "prayer_times" -> binding.bottomNavigation.selectedItemId = R.id.nav_prayer_times
            "qibla" -> binding.bottomNavigation.selectedItemId = R.id.nav_qibla
        }
    }

    override fun onDestroy() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_DESTROY", mapOf("activity" to "MainActivity"))
        MosqueDNDApp.prefs.unregisterOnSharedPreferenceChangeListener(hijriPrefListener)
        super.onDestroy()
    }

    override fun onPause() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_PAUSE", mapOf("activity" to "MainActivity"))
        super.onPause()
    }

    override fun onResume() {
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.STARTUP, "ACTIVITY_RESUME", mapOf("activity" to "MainActivity"))
        super.onResume()
        clearStalePref()

        // Show background location rationale if monitoring was enabled but background
        // location permission was revoked — the foreground service cannot restart without it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val needsBgLocation = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            if (needsBgLocation) {
                val monitoringEnabled = MosqueDNDApp.prefs.getBoolean(PREF_MONITORING_ENABLED, false)
                if (monitoringEnabled && !LocationMonitorService.isServiceRunning()
                    && ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    Log.d(TAG, "Background location missing while monitoring enabled — showing rationale")
                    BackgroundLocationRationaleFragment.show(this)
                    return
                }
            }
        }

        // Re-verify usage access grant if user just returned from Settings
        if (pendingUsageAccessGrant) {
            val appOpsManager = getSystemService(AppOpsManager::class.java)
            if (appOpsManager != null) {
                val mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    showSnackbar(getString(R.string.usage_access_granted))
                } else {
                    showSnackbar(getString(R.string.usage_access_denied))
                }
            }
            pendingUsageAccessGrant = false
        }

        // One-shot navigation check for widget-launched intents.
        // Also covers the case where the flag survived a configuration change or
        // was set by the onboarding-finished relaunch (saved to prefs by onCreate).
        if (pendingNavigateToPrayerTimes) {
            pendingNavigateToPrayerTimes = false
            if (binding.bottomNavigation.selectedItemId != R.id.nav_prayer_times) {
                binding.bottomNavigation.selectedItemId = R.id.nav_prayer_times
            }
        }
    }

    private fun clearStalePref() {
        val persisted = MosqueDNDApp.prefs.getBoolean(PREF_MONITORING_ENABLED, false)
        if (persisted && !LocationMonitorService.isServiceRunning()) {
            Log.w(TAG, "Stale monitoring pref detected — restarting service")
            val savedInterval = MosqueDNDApp.prefs.getInt(PREF_MONITORING_INTERVAL, 30)
            LocationMonitorService.startService(this, savedInterval)
        }
    }

    private fun setupBottomNavigation() {
        binding.viewPager.isUserInputEnabled = true  // enables swipe

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val tabName = when (item.itemId) {
                R.id.nav_home -> "Home"
                R.id.nav_mosques -> "Mosques"
                R.id.nav_prayer_times -> "PrayerTimes"
                R.id.nav_qibla -> "Qibla"
                else -> "Unknown"
            }
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.NAVIGATION, "TAB_SELECTED", mapOf("tab" to tabName, "via" to "click"))
            when (item.itemId) {
                R.id.nav_home -> { binding.viewPager.currentItem = 0; true }
                R.id.nav_mosques -> { binding.viewPager.currentItem = 1; true }
                R.id.nav_prayer_times -> { binding.viewPager.currentItem = 2; true }
                R.id.nav_qibla -> { binding.viewPager.currentItem = 3; true }
                else -> false
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val tabName = when (position) {
                    0 -> "Home"
                    1 -> "Mosques"
                    2 -> "PrayerTimes"
                    3 -> "Qibla"
                    else -> "Unknown"
                }
                LogHelper.logTelemetry(LogHelper.TelemetryCategory.NAVIGATION, "TAB_SELECTED", mapOf("tab" to tabName, "via" to "swipe"))
                val itemId = when (position) {
                    0 -> R.id.nav_home
                    1 -> R.id.nav_mosques
                    2 -> R.id.nav_prayer_times
                    3 -> R.id.nav_qibla
                    else -> return
                }
                binding.bottomNavigation.selectedItemId = itemId
            }
        })

        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.viewPager.setCurrentItem(0, false)

        // Post-layout verification: log actual item spacing / height for debugging
        binding.bottomNavigation.post {
            val actualHeight = binding.bottomNavigation.height
            val expectedHeight = resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
            Log.d(TAG, "BottomNavigationView height: ${actualHeight}px (expected: ${expectedHeight}px)")
            if (actualHeight < expectedHeight) {
                Log.w(TAG, "BottomNavigationView height (${actualHeight}px) is less than expected (${expectedHeight}px) — items may overlap")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    fun showSettingsFragment() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsFragment()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateHijriSubtitle() {
        val hijriDate = IslamicCalendar.getCurrentHijriDate()
        val formatted = IslamicCalendar.format(hijriDate)
        val prefix = getString(R.string.hijri_date_prefix)
        supportActionBar?.subtitle = "$prefix$formatted"
    }

    private fun setupAndShowUI() {
        updateHijriSubtitle()
        MosqueDNDApp.prefs.registerOnSharedPreferenceChangeListener(hijriPrefListener)

        if (pendingNavigateToPrayerTimes) {
            pendingNavigateToPrayerTimes = false
            binding.bottomNavigation.selectedItemId = R.id.nav_prayer_times
        }

        if (WhatsNewManager.shouldShow(this)) {
            WhatsNewBottomSheetFragment.show(supportFragmentManager)
        }
    }


    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
