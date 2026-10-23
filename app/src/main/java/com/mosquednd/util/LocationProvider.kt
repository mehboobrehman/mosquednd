package com.mosquednd.util

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import com.mosquednd.util.LogHelper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mosquednd.MosqueDNDApp

/**
 * Centralized location provider that caches the last known location in SharedPreferences
 * and provides both synchronous (cached) and async (fresh GPS) access.
 * Falls back to Karachi coordinates when no cached location exists and permission is denied.
 */
object LocationProvider {

    private const val TAG = "LocationProvider"
    private const val PREFS_KEY_LAT = "last_known_lat"
    private const val PREFS_KEY_LNG = "last_known_lng"
    private const val PREFS_KEY_HAS_LOCATION = "has_cached_location"
    private const val PREFS_KEY_LOCATION_VERSION = "location_version"

    // Locations with horizontal accuracy worse than this (meters) are rejected as
    // unreliable — even when not flagged as a mock provider. See task: mock-location
    // detection + accuracy quality gate.
    private const val MAX_ACCEPTABLE_ACCURACY_M = 200f

    // Manual override keys
    const val PREF_LOCATION_OVERRIDE_LAT = "location_override_lat"
    const val PREF_LOCATION_OVERRIDE_LNG = "location_override_lng"

    // Karachi fallback coordinates
    private const val DEFAULT_LAT = 24.8607
    private const val DEFAULT_LNG = 67.0011

    private lateinit var appContext: Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var encryptedPrefs: SharedPreferences? = null

    /**
     * Resolves the active SharedPreferences: encrypted if initialized, otherwise
     * regular MosqueDNDApp.prefs (fallback during Direct Boot or before init).
     */
    private val prefs get() = encryptedPrefs ?: MosqueDNDApp.prefs

    /**
     * Initialize with Application context. Call from MosqueDNDApp.onCreate().
     */
    fun init(app: Application) {
        appContext = app.applicationContext
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(app)
        LogHelper.logd(TAG, "LocationProvider initialized")
    }

    /**
     * Initialize with encrypted SharedPreferences for at-rest encryption of
     * sensitive location cache and override data. Called from MosqueDNDApp.onCreate()
     * after the basic [init]. Falls back to regular MosqueDNDApp.prefs if not
     * initialized (e.g. during Direct Boot).
     */
    fun initEncryptedPrefs(encryptedPrefs: SharedPreferences) {
        this.encryptedPrefs = encryptedPrefs
        LogHelper.logd(TAG, "Encrypted SharedPreferences initialized")
    }

    /**
     * Returns cached lat/lng from SharedPreferences, or Karachi default if none cached.
     * This is synchronous and safe to call from any thread (reads SharedPreferences).
     */
    fun getLastKnownLocation(): Pair<Double, Double> {
        val hasCached = prefs.getBoolean(PREFS_KEY_HAS_LOCATION, false)

        return if (hasCached) {
            val lat = prefs.getFloat(PREFS_KEY_LAT, DEFAULT_LAT.toFloat()).toDouble()
            val lng = prefs.getFloat(PREFS_KEY_LNG, DEFAULT_LNG.toFloat()).toDouble()
            Pair(lat, lng)
        } else {
            LogHelper.logd(TAG, "No cached location, returning Karachi default")
            Pair(DEFAULT_LAT, DEFAULT_LNG)
        }
    }

    /**
     * Overload for convenience accepting Context (ignored as application context is cached).
     */
    fun getLastKnownLocation(context: Context?): Pair<Double, Double> = getLastKnownLocation()

    /**
     * Requests fresh GPS location via FusedLocationProviderClient.
     * On success, caches the result in SharedPreferences and calls onResult.
     * On failure, calls onResult with cached or default coordinates.
     */
    fun requestFreshLocation(onResult: (Double, Double) -> Unit) {
        if (!hasLocationPermission()) {
            LogHelper.logw(TAG, "Location permission not granted, using cached/default")
            val (lat, lng) = getLastKnownLocation()
            onResult(lat, lng)
            return
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val provider = location.provider ?: "unknown"
                        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            location.isMock
                        } else {
                            @Suppress("DEPRECATION")
                            location.isFromMockProvider
                        }

                        // Mitigation: only check isFromMockProvider when accuracy > 200m (poor) 
                        // or if we have high-accuracy FINE_LOCATION (assumed since we have permission)
                        if (isMock && (location.accuracy > MAX_ACCEPTABLE_ACCURACY_M || hasLocationPermission())) {
                            LogHelper.logw(TAG, "Mock location detected (provider=$provider) — rejecting, using cached/default")
                            val (lat, lng) = getLastKnownLocation()
                            onResult(lat, lng)
                            return@addOnSuccessListener
                        }
                        if (location.accuracy > MAX_ACCEPTABLE_ACCURACY_M) {
                            LogHelper.logw(TAG, "Location accuracy ${location.accuracy}m exceeds threshold ${MAX_ACCEPTABLE_ACCURACY_M}m — rejecting, using cached/default")
                            val (lat, lng) = getLastKnownLocation()
                            onResult(lat, lng)
                            return@addOnSuccessListener
                        }
                        cacheLocation(location.latitude, location.longitude)
                        LogHelper.logLocation(TAG, location.latitude, location.longitude)
                        LogHelper.logd(TAG, "Fresh location info: provider=$provider, accuracy=${location.accuracy}m")
                        onResult(location.latitude, location.longitude)
                    } else {
                        LogHelper.logw(TAG, "FusedLocation returned null, using cached/default")
                        val (lat, lng) = getLastKnownLocation()
                        onResult(lat, lng)
                    }
                }
                .addOnFailureListener { e ->
                    LogHelper.loge(TAG, "Failed to get fresh location (GPS signal lost, location services disabled, or timeout): ${e.message}")
                    val (lat, lng) = getLastKnownLocation()
                    onResult(lat, lng)
                }
        } catch (e: SecurityException) {
            LogHelper.loge(TAG, "SecurityException accessing location: ${e.message}")
            val (lat, lng) = getLastKnownLocation()
            onResult(lat, lng)
        }
    }

    /**
     * Cache a location update in SharedPreferences.
     * Also increments the location version counter so observers can detect changes.
     */
    fun cacheLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(PREFS_KEY_LAT, lat.toFloat())
            .putFloat(PREFS_KEY_LNG, lng.toFloat())
            .putBoolean(PREFS_KEY_HAS_LOCATION, true)
            .putInt(PREFS_KEY_LOCATION_VERSION, getLocationVersion() + 1)
            .apply()
        LogHelper.logLocation(TAG, lat, lng)
    }

    /**
     * Override the user's location with a manually entered value.
     * This persists across app restarts via SharedPreferences.
     * Cleared when [clearLocationOverride] is called.
     * Also increments the location version counter so observers can detect changes.
     */
    fun setLocationOverride(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(PREF_LOCATION_OVERRIDE_LAT, lat.toFloat())
            .putFloat(PREF_LOCATION_OVERRIDE_LNG, lng.toFloat())
            .putInt(PREFS_KEY_LOCATION_VERSION, getLocationVersion() + 1)
            .apply()
        LogHelper.logLocation(TAG, lat, lng)
    }

    /**
     * Clear the manual location override. After calling this,
     * [getEffectiveLocation] falls back to cached GPS (or default).
     * Increments the location version since the effective coordinates change.
     */
    fun clearLocationOverride() {
        prefs.edit()
            .remove(PREF_LOCATION_OVERRIDE_LAT)
            .remove(PREF_LOCATION_OVERRIDE_LNG)
            .putInt(PREFS_KEY_LOCATION_VERSION, getLocationVersion() + 1)
            .apply()
        LogHelper.logd(TAG, "Location override cleared")
    }

    /**
     * Returns true if a manual location override has been set via [setLocationOverride].
     */
    fun hasLocationOverride(): Boolean {
        return prefs.contains(PREF_LOCATION_OVERRIDE_LAT)
    }

    /**
     * Returns the effective location using this priority:
     * 1. Manual location override (if set)
     * 2. Cached GPS location (from previous fresh fix)
     * 3. Karachi default fallback
     */
    fun getEffectiveLocation(): Pair<Double, Double> {
        if (hasLocationOverride()) {
            val lat = prefs.getFloat(PREF_LOCATION_OVERRIDE_LAT, 0f).toDouble()
            val lng = prefs.getFloat(PREF_LOCATION_OVERRIDE_LNG, 0f).toDouble()
            LogHelper.logLocation(TAG, lat, lng)
            return Pair(lat, lng)
        }
        return getLastKnownLocation()
    }

    /**
     * Returns true if a reliable location exists (either cached GPS or manual override).
     * When false, the UI should show an error prompt because no location data is available
     * (the Karachi default is NOT considered reliable).
     */
    fun isLocationReliable(): Boolean {
        return hasLocationOverride() || prefs.getBoolean(PREFS_KEY_HAS_LOCATION, false)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ──────────────────────────────────────────────
    // Location version tracking (for reschedule on change)
    // ──────────────────────────────────────────────

    /**
     * Returns the current location version counter from SharedPreferences.
     * Incremented by [cacheLocation], [setLocationOverride], and [clearLocationOverride].
     * Observers can compare this value between calls to detect when location data changed.
     */
    fun getLocationVersion(): Int {
        return prefs.getInt(PREFS_KEY_LOCATION_VERSION, 0)
    }

    /**
     * Returns the JVM timezone ID (e.g. "Asia/Karachi") currently in effect.
     * Used by observers to detect timezone changes between fragment sessions.
     */
    fun getLastKnownTimezoneId(): String {
        return java.util.TimeZone.getDefault().id
    }
}
