package com.mosquednd.util

import android.util.Log
import com.mosquednd.MosqueDNDApp

/**
 * Caches Overpass API responses in SharedPreferences so mosque data
 * persists offline and avoids redundant network calls.
 *
 * Cache entries are keyed by rounded lat/lng (0.01° precision) + radius
 * and expire after a configurable number of days (default 30, range 1–90).
 */
object PlaceDetectorCache {

    private const val TAG = "PlaceDetectorCache"
    private const val PREFS_CACHE_EXPIRY_DAYS = "cache_expiry_days"
    private const val DEFAULT_EXPIRY_DAYS = 30
    private const val MIN_EXPIRY_DAYS = 1
    private const val MAX_EXPIRY_DAYS = 90
    private const val TIMESTAMP_SUFFIX = "_ts"

    private val prefs get() = MosqueDNDApp.prefs

    /**
     * Compute a cache key from location and radius, with lat/lng rounded
     * to 0.01° precision for spatial reuse.
     * Format: "cache_{rounded_lat}_{rounded_lng}_{radius}"
     */
    fun calculateCacheKey(lat: Double, lng: Double, radius: Int): String {
        val roundedLat = Math.round(lat * 100.0) / 100.0
        val roundedLng = Math.round(lng * 100.0) / 100.0
        return "cache_${roundedLat}_${roundedLng}_$radius"
    }

    /**
     * Returns cached JSON for the given key, or null if expired or missing.
     */
    fun get(key: String): String? {
        if (isExpired(key)) {
            Log.d(TAG, "Cache expired for key: $key")
            return null
        }
        val json = prefs.getString(key, null)
        if (json != null) {
            Log.d(TAG, "Cache hit for key: $key")
        }
        return json
    }

    /**
     * Stores JSON response with the current timestamp.
     */
    fun put(key: String, json: String) {
        prefs.edit()
            .putString(key, json)
            .putLong("$key$TIMESTAMP_SUFFIX", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Cached $key (${json.length} bytes)")
    }

    /**
     * Returns true if the cache entry for the given key has exceeded the
     * configured expiry duration (in days). Returns true if no entry exists.
     */
    fun isExpired(key: String): Boolean {
        val timestamp = getCacheTimestamp(key) ?: return true
        val expiryDays = getExpiryDays().coerceIn(MIN_EXPIRY_DAYS, MAX_EXPIRY_DAYS)
        val expiryMs = expiryDays * 24L * 60L * 60L * 1000L
        return System.currentTimeMillis() - timestamp > expiryMs
    }

    /**
     * Returns the stored timestamp for a cache entry, or null if missing.
     */
    fun getCacheTimestamp(key: String): Long? {
        val ts = prefs.getLong("$key$TIMESTAMP_SUFFIX", -1L)
        return if (ts == -1L) null else ts
    }

    /**
     * Removes all cache entries (strings starting with "cache_") from prefs.
     */
    fun clearAll() {
        val keysToRemove = prefs.all.keys.filter { it.startsWith("cache_") }
        prefs.edit().apply {
            keysToRemove.forEach { remove(it) }
            apply()
        }
        Log.d(TAG, "Cleared ${keysToRemove.size} cache entries")
    }

    private fun getExpiryDays(): Int {
        return prefs.getInt(PREFS_CACHE_EXPIRY_DAYS, DEFAULT_EXPIRY_DAYS)
    }
}
