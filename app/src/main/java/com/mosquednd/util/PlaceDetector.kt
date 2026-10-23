package com.mosquednd.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mapbox.mapboxsdk.geometry.LatLng
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.io.IOException
import java.net.URLEncoder
import java.net.URL
import org.json.JSONObject

object PlaceDetector {
    
    private const val TAG = "PlaceDetector"

    data class MosquePlace(
        val name: String,
        val latLng: LatLng?,
        val address: String
    )

    /**
     * Enum representing granular Overpass API error states.
     */
    enum class OverpassError {
        NONE,
        TIMEOUT,
        RATE_LIMITED,
        SERVER_UNAVAILABLE,
        NETWORK_IO,
        UNKNOWN
    }

    // Thread-safe / volatile last error tracker for UI feedback
    @Volatile
    var lastError: OverpassError = OverpassError.NONE
        private set

    /**
     * Find nearby mosques from Overpass API, with caching and granular error state tracking.
     *
     * @param location  Center point for the search.
     * @param radius    Search radius in metres (default 5000).
     * @param forceRefresh  If true, bypass the cache and always call the API.
     * @return List of [MosquePlace] found near the location.
     */
    suspend fun findNearbyMosques(
        location: LatLng,
        radius: Int = 5000,
        forceRefresh: Boolean = false
    ): List<MosquePlace> = withContext(Dispatchers.IO) {
        lastError = OverpassError.NONE
        val cacheKey = PlaceDetectorCache.calculateCacheKey(
            location.latitude,
            location.longitude,
            radius
        )

        // Check cache first (unless forceRefresh is requested)
        if (!forceRefresh) {
            val cachedJson = PlaceDetectorCache.get(cacheKey)
            if (cachedJson != null) {
                Log.d(TAG, "Cache hit for $cacheKey — using cached data")
                val cachedResults = parseOverpassResponse(cachedJson)
                return@withContext cachedResults
            }
            Log.d(TAG, "Cache miss for $cacheKey")
        } else {
            Log.d(TAG, "Force refresh — bypassing cache for $cacheKey")
        }

        // Fetch fresh data from Overpass
        val json = fetchRawOverpassJson(location, radius)
        if (json == null) {
            Log.e(TAG, "All Overpass servers failed. Last error state: $lastError")
            return@withContext emptyList()
        }

        val results = parseOverpassResponse(json)

        // Only cache non-empty results to avoid caching empty responses
        if (results.isNotEmpty()) {
            PlaceDetectorCache.put(cacheKey, json)
            Log.d(TAG, "Cached $cacheKey (${results.size} mosques)")
        } else {
            Log.d(TAG, "Overpass returned empty results — not caching")
        }

        return@withContext results
    }
    
    private val overpassServers = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    /**
     * Fetch raw JSON response from Overpass API. Tries each server in order.
     * @return Raw JSON string, or null if all servers failed.
     */
    private fun fetchRawOverpassJson(location: LatLng, radius: Int): String? {
        val lat = location.latitude
        val lng = location.longitude
        
        val query = """
            [out:json][timeout:25];
            (
              node["amenity"="place_of_worship"]["religion"="muslim"](around:$radius,$lat,$lng);
              way["amenity"="place_of_worship"]["religion"="muslim"](around:$radius,$lat,$lng);
              relation["amenity"="place_of_worship"]["religion"="muslim"](around:$radius,$lat,$lng);
            );
            out center;
        """.trimIndent()
        
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        for (serverUrl in overpassServers) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$serverUrl?data=$encodedQuery")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "MosqueDND/1.0 (Android; android@mosquednd.com)")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Success fetching from: $serverUrl")
                    lastError = OverpassError.NONE
                    return response
                } else {
                    Log.w(TAG, "Server $serverUrl returned HTTP error: $responseCode")
                    when (responseCode) {
                        429 -> {
                            lastError = OverpassError.RATE_LIMITED
                            Log.w(TAG, "Rate limit exceeded (HTTP 429) on Overpass server: $serverUrl")
                        }
                        503, 504 -> {
                            lastError = OverpassError.SERVER_UNAVAILABLE
                            Log.w(TAG, "Overpass server unavailable or gateway timeout (HTTP $responseCode): $serverUrl")
                        }
                        else -> {
                            lastError = OverpassError.SERVER_UNAVAILABLE
                            Log.w(TAG, "Overpass server returned error code $responseCode on: $serverUrl")
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                lastError = OverpassError.TIMEOUT
                Log.w(TAG, "API timeout connecting to Overpass server: $serverUrl — ${e.message}", e)
            } catch (e: IOException) {
                lastError = OverpassError.NETWORK_IO
                Log.w(TAG, "Network IO failure connecting to Overpass server: $serverUrl — ${e.message}, trying next server", e)
            } catch (e: Exception) {
                lastError = OverpassError.UNKNOWN
                Log.e(TAG, "Unexpected error fetching from Overpass server: $serverUrl — ${e.message}", e)
            } finally {
                connection?.disconnect()
            }
        }

        return null
    }
    
    private fun parseOverpassResponse(json: String): List<MosquePlace> {
        val results = mutableListOf<MosquePlace>()
        
        try {
            val jsonObject = JSONObject(json)
            val elements = jsonObject.getJSONArray("elements")
            
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                
                val lat: Double
                val lon: Double
                if (element.has("lat") && element.has("lon")) {
                    lat = element.getDouble("lat")
                    lon = element.getDouble("lon")
                } else if (element.has("center")) {
                    val center = element.getJSONObject("center")
                    lat = center.getDouble("lat")
                    lon = center.getDouble("lon")
                } else {
                    continue
                }
                val latLng = LatLng(lat, lon)
                
                val tags = element.optJSONObject("tags")
                val name = tags?.optString("name") ?: tags?.optString("name:en") ?: "Mosque"
                val address = buildAddress(tags)
                
                results.add(MosquePlace(name, latLng, address))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Overpass response", e)
        }
        
        return results
    }
    
    private fun buildAddress(tags: JSONObject?): String {
        if (tags == null) return ""
        
        val parts = mutableListOf<String>()
        tags.optString("addr:housenumber").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        tags.optString("addr:street").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        tags.optString("addr:city").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        
        return if (parts.isEmpty()) {
            tags.optString("addr:full", "")
        } else {
            parts.joinToString(", ")
        }
    }
}
