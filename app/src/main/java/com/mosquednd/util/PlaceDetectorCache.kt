package com.mosquednd.util

import android.os.Looper
import android.util.Log
import com.mosquednd.MosqueDNDApp
import com.mosquednd.data.MosqueDatabase
import com.mosquednd.data.NearbyMosqueCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Caches Overpass API responses in Room database so mosque data
 * persists offline and avoids redundant network calls.
 *
 * Replaces legacy SharedPreferences caching to prevent in-memory HashMap heap bloat
 * on low-RAM devices (e.g., 2GB RAM).
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

    private val prefs get() = MosqueDNDApp.prefs
    private val cacheDao get() = MosqueDatabase.getInstance(MosqueDNDApp.instance).nearbyMosqueCacheDao()

    init {
        purgeLegacyPrefsCache()
    }

    private fun <T> runOnIo(block: () -> T): T {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            runBlocking(Dispatchers.IO) { block() }
        } else {
            block()
        }
    }

    /**
     * One-time purge of legacy SharedPreferences JSON cache strings to free up memory.
     * Preserves [PREFS_CACHE_EXPIRY_DAYS] preference.
     */
    private fun purgeLegacyPrefsCache() {
        try {
            val legacyKeys = prefs.all.keys.filter {
                it.startsWith("cache_") && it != PREFS_CACHE_EXPIRY_DAYS
            }
            if (legacyKeys.isNotEmpty()) {
                val editor = prefs.edit()
                legacyKeys.forEach { editor.remove(it) }
                editor.apply()
                Log.d(TAG, "Purged ${legacyKeys.size} legacy SharedPreferences cache entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging legacy prefs cache", e)
        }
    }

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
    fun get(key: String): String? = runOnIo {
        val entity = cacheDao.get(key) ?: return@runOnIo null
        val expiryDays = getExpiryDays().coerceIn(MIN_EXPIRY_DAYS, MAX_EXPIRY_DAYS)
        val expiryMs = expiryDays * 24L * 60L * 60L * 1000L
        if (System.currentTimeMillis() - entity.timestamp > expiryMs) {
            Log.d(TAG, "Cache expired for key: $key")
            null
        } else {
            Log.d(TAG, "Cache hit for key: $key (${entity.jsonPayload.length} bytes)")
            entity.jsonPayload
        }
    }

    /**
     * Stores JSON response with the current timestamp in Room.
     */
    fun put(key: String, json: String) {
        runOnIo {
            cacheDao.put(
                NearbyMosqueCacheEntity(
                    cacheKey = key,
                    timestamp = System.currentTimeMillis(),
                    jsonPayload = json
                )
            )
            Log.d(TAG, "Cached $key (${json.length} bytes) in Room")
        }
    }

    /**
     * Returns true if the cache entry for the given key has exceeded the
     * configured expiry duration (in days). Returns true if no entry exists.
     */
    fun isExpired(key: String): Boolean = runOnIo {
        val timestamp = getCacheTimestamp(key) ?: return@runOnIo true
        val expiryDays = getExpiryDays().coerceIn(MIN_EXPIRY_DAYS, MAX_EXPIRY_DAYS)
        val expiryMs = expiryDays * 24L * 60L * 60L * 1000L
        System.currentTimeMillis() - timestamp > expiryMs
    }

    /**
     * Returns the stored timestamp for a cache entry, or null if missing.
     */
    fun getCacheTimestamp(key: String): Long? = runOnIo {
        cacheDao.get(key)?.timestamp
    }

    /**
     * Removes all cache entries from Room database.
     */
    fun clearAll() {
        runOnIo {
            cacheDao.clearAll()
            Log.d(TAG, "Cleared all entries from Room cache")
        }
    }

    private fun getExpiryDays(): Int {
        return prefs.getInt(PREFS_CACHE_EXPIRY_DAYS, DEFAULT_EXPIRY_DAYS)
    }
}
