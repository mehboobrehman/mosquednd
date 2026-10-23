package com.mosquednd.util

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mosquednd.MosqueDNDApp
import com.mosquednd.R
import java.util.Calendar
import java.util.TimeZone

/**
 * Robust Location Provider supporting encrypted SharedPreferences, fallbacks,
 * mock location filtering, and granular user error feedback (GPS waiting, permissions, timeout).
 */
object LocationProvider {

    private const val TAG = "LocationProvider"
    private const val PREF_NAME = "location_provider"
    private const val PREF_LOCATION_LAT = "cached_location_lat"
    private const val PREF_LOCATION_LNG = "cached_location_lng"
    private const val PREF_LOCATION_VERSION = "cached_location_version"
    private const val PREF_LOCATION_TIMESTAMP = "cached_location_timestamp"

    private const val PREF_LOCATION_OVERRIDE_LAT = "location_override_lat"
    private const val PREF_LOCATION_OVERRIDE_LNG = "location_override_lng"

    // Default fallback: Gulshan-e-Hadeed, Karachi
    private const val DEFAULT_LAT = 24.8607
    private const val DEFAULT_LNG = 67.1118

    // Acceptable accuracy threshold in meters (e.g., 500m)
    private const val MAX_ACCEPTABLE_ACCURACY_M = 500.0

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var encryptedPrefs: SharedPreferences? = null

    /**
     * Initialize with Application context or EncryptedSharedPreferences.
     */
    fun init(app: Application) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(app)
        try {
            encryptedPrefs = EncryptedPrefsManager.getEncryptedPrefs(app, PREF_NAME)
            LogHelper.logd(TAG, "Initialized EncryptedSharedPreferences for LocationProvider")
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Failed to init EncryptedSharedPreferences, falling back to standard prefs: ${e.message}")
            encryptedPrefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun initEncryptedPrefs(prefs: SharedPreferences) {
        encryptedPrefs = prefs
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(MosqueDNDApp.instance)
        } catch (e: Exception) {
            // Ignored if instance not ready yet
        }
    }

    private val prefs get() = encryptedPrefs ?: MosqueDNDApp.prefs

    /**
     * Checks if location permission (fine or coarse) is granted.
     */
    fun hasLocationPermission(): Boolean {
        val context = MosqueDNDApp.instance
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    /**
     * Checks if GPS / Location services are enabled on the device.
     */
    fun isLocationEnabled(): Boolean {
        val context = MosqueDNDApp.instance
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    /**
     * Request fresh location with explicit granular error handling and user feedback.
     */
    fun requestFreshLocation(context: Context, onResult: (lat: Double, lng: Double) -> Unit) {
        // 1. Check permissions
        if (!hasLocationPermission()) {
            LogHelper.logw(TAG, "Location permission denied")
            Toast.makeText(context, context.getString(R.string.error_permissions_denied), Toast.LENGTH_SHORT).show()
            val (lat, lng) = getLastKnownLocation()
            onResult(lat, lng)
            return
        }

        // 2. Check if GPS/Location services are enabled
        if (!isLocationEnabled()) {
            LogHelper.logw(TAG, "GPS disabled")
            Toast.makeText(context, context.getString(R.string.error_gps_disabled), Toast.LENGTH_SHORT).show()
            val (lat, lng) = getLastKnownLocation()
            onResult(lat, lng)
            return
        }

        // 3. Show "Waiting for GPS" toast/feedback
        Toast.makeText(context, context.getString(R.string.error_waiting_for_gps), Toast.LENGTH_SHORT).show()

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            location.isMock
                        } else {
                            @Suppress("DEPRECATION")
                            location.isFromMockProvider
                        }

                        if (isMock && (location.accuracy > MAX_ACCEPTABLE_ACCURACY_M || hasLocationPermission())) {
                            LogHelper.logw(TAG, "Mock location detected — rejecting, using cached/default")
                            Toast.makeText(context, context.getString(R.string.location_unavailable_title), Toast.LENGTH_SHORT).show()
                            val (lat, lng) = getLastKnownLocation()
                            onResult(lat, lng)
                            return@addOnSuccessListener
                        }
                        if (location.accuracy > MAX_ACCEPTABLE_ACCURACY_M) {
                            LogHelper.logw(TAG, "Location accuracy exceeds threshold — rejecting")
                            Toast.makeText(context, context.getString(R.string.compass_low_accuracy), Toast.LENGTH_SHORT).show()
                            val (lat, lng) = getLastKnownLocation()
                            onResult(lat, lng)
                            return@addOnSuccessListener
                        }
                        cacheLocation(location.latitude, location.longitude)
                        LogHelper.logLocation(TAG, location.latitude, location.longitude)
                        onResult(location.latitude, location.longitude)
                    } else {
                        LogHelper.logw(TAG, "FusedLocation returned null, API timeout / no fix yet")
                        Toast.makeText(context, context.getString(R.string.error_api_timeout), Toast.LENGTH_SHORT).show()
                        val (lat, lng) = getLastKnownLocation()
                        onResult(lat, lng)
                    }
                }
                .addOnFailureListener { e ->
                    LogHelper.loge(TAG, "Failed to get fresh location (API timeout / error): ${e.message}")
                    Toast.makeText(context, context.getString(R.string.error_api_timeout), Toast.LENGTH_SHORT).show()
                    val (lat, lng) = getLastKnownLocation()
                    onResult(lat, lng)
                }
        } catch (e: SecurityException) {
            LogHelper.loge(TAG, "SecurityException accessing location: ${e.message}")
            Toast.makeText(context, context.getString(R.string.error_permissions_denied), Toast.LENGTH_SHORT).show()
            val (lat, lng) = getLastKnownLocation()
            onResult(lat, lng)
        }
    }

    /**
     * Cache a location update in SharedPreferences.
     */
    fun cacheLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(PREF_LOCATION_LAT, lat.toFloat())
            .putFloat(PREF_LOCATION_LNG, lng.toFloat())
            .putLong(PREF_LOCATION_TIMESTAMP, System.currentTimeMillis())
            .putLong(PREF_LOCATION_VERSION, prefs.getLong(PREF_LOCATION_VERSION, 0L) + 1L)
            .apply()
    }

    /**
     * Returns the last known cached location or defaults.
     */
    fun getLastKnownLocation(): Pair<Double, Double> {
        val lat = prefs.getFloat(PREF_LOCATION_LAT, DEFAULT_LAT.toFloat()).toDouble()
        val lng = prefs.getFloat(PREF_LOCATION_LNG, DEFAULT_LNG.toFloat()).toDouble()
        return Pair(lat, lng)
    }

    fun getLocationVersion(): Int {
        return prefs.getLong(PREF_LOCATION_VERSION, 0L).toInt()
    }

    fun getLastKnownTimezoneId(): String {
        return TimeZone.getDefault().id
    }

    fun isLocationReliable(): Boolean {
        return hasLocationPermission() && isLocationEnabled()
    }

    fun setLocationOverride(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(PREF_LOCATION_OVERRIDE_LAT, lat.toFloat())
            .putFloat(PREF_LOCATION_OVERRIDE_LNG, lng.toFloat())
            .apply()
        LogHelper.logd(TAG, "Location override set: $lat, $lng")
    }

    fun clearLocationOverride() {
        prefs.edit()
            .remove(PREF_LOCATION_OVERRIDE_LAT)
            .remove(PREF_LOCATION_OVERRIDE_LNG)
            .apply()
        LogHelper.logd(TAG, "Location override cleared")
    }

    fun hasLocationOverride(): Boolean {
        return prefs.contains(PREF_LOCATION_OVERRIDE_LAT)
    }

    fun getEffectiveLocation(): Pair<Double, Double> {
        if (hasLocationOverride()) {
            val lat = prefs.getFloat(PREF_LOCATION_OVERRIDE_LAT, DEFAULT_LAT.toFloat()).toDouble()
            val lng = prefs.getFloat(PREF_LOCATION_OVERRIDE_LNG, DEFAULT_LNG.toFloat()).toDouble()
            return Pair(lat, lng)
        }
        return getLastKnownLocation()
    }
}
