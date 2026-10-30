package com.mosquednd.config

import android.content.Context
import android.util.Log
import com.mosquednd.util.AgentLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RemoteConfigManager: Manages fetching and applying remote configuration and feature flags
 * from the telemetry server's /api/config endpoint.
 */
object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"
    private const val PREFS_NAME = "remote_config_prefs"

    // Feature flag keys / config keys
    private const val KEY_FORCE_401 = "force401Error"
    private const val KEY_ENABLE_VERBOSE_LOGGING = "enableVerboseLogging"
    private const val KEY_SYNC_INTERVAL_MINUTES = "syncIntervalMinutes"
    private const val KEY_MAINTENANCE_MODE = "maintenanceMode"

    @Volatile
    var force401Error: Boolean = false
        private set

    @Volatile
    var enableVerboseLogging: Boolean = false
        private set

    @Volatile
    var syncIntervalMinutes: Int = 15
        private set

    @Volatile
    var maintenanceMode: Boolean = false
        private set

    private val isFetching = AtomicBoolean(false)
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initialize config manager, load persisted local override/cached values,
     * and start periodic background polling if desired.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        force401Error = prefs.getBoolean(KEY_FORCE_401, false)
        enableVerboseLogging = prefs.getBoolean(KEY_ENABLE_VERBOSE_LOGGING, false)
        syncIntervalMinutes = prefs.getInt(KEY_SYNC_INTERVAL_MINUTES, 15)
        maintenanceMode = prefs.getBoolean(KEY_MAINTENANCE_MODE, false)

        Log.d(TAG, "RemoteConfig initialized: force401=$force401Error, verbose=$enableVerboseLogging, syncInterval=$syncIntervalMinutes, maintenance=$maintenanceMode")

        // Start background polling for config updates
        startPolling(context)
    }

    /**
     * Fetch configuration from the active server's /api/config endpoint.
     */
    fun fetchConfig(context: Context, onComplete: ((Boolean, String?) -> Unit)? = null) {
        if (!isFetching.compareAndSet(false, true)) {
            onComplete?.invoke(false, "Already fetching")
            return
        }

        scope.launch {
            try {
                val serverIp = AgentLogger.getServerIp(context)
                if (serverIp.isNullOrEmpty()) {
                    isFetching.set(false)
                    onComplete?.invoke(false, "No server IP available")
                    return@launch
                }

                val baseUrl = if (serverIp.startsWith("http")) serverIp else "http://$serverIp:5000"
                val url = URL("$baseUrl/api/config")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseString)

                    // Parse config properties
                    val newForce401 = json.optBoolean(KEY_FORCE_401, force401Error)
                    val newVerbose = json.optBoolean(KEY_ENABLE_VERBOSE_LOGGING, enableVerboseLogging)
                    val newSyncInterval = json.optInt(KEY_SYNC_INTERVAL_MINUTES, syncIntervalMinutes)
                    val newMaintenance = json.optBoolean(KEY_MAINTENANCE_MODE, maintenanceMode)

                    // Update cached in-memory state and persist
                    force401Error = newForce401
                    enableVerboseLogging = newVerbose
                    syncIntervalMinutes = newSyncInterval
                    maintenanceMode = newMaintenance

                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean(KEY_FORCE_401, force401Error)
                        .putBoolean(KEY_ENABLE_VERBOSE_LOGGING, enableVerboseLogging)
                        .putInt(KEY_SYNC_INTERVAL_MINUTES, syncIntervalMinutes)
                        .putBoolean(KEY_MAINTENANCE_MODE, maintenanceMode)
                        .apply()

                    Log.i(TAG, "Successfully fetched and applied remote config from $serverIp")
                    isFetching.set(false)
                    onComplete?.invoke(true, null)
                } else {
                    Log.w(TAG, "Failed to fetch remote config: HTTP $responseCode")
                    isFetching.set(false)
                    onComplete?.invoke(false, "HTTP $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Exception fetching remote config: ${e.message}")
                isFetching.set(false)
                onComplete?.invoke(false, e.message)
            }
        }
    }

    /**
     * Allow manual overrides from Debug Drawer or settings.
     */
    fun setFeatureFlag(context: Context, key: String, value: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        when (key) {
            KEY_FORCE_401 -> {
                force401Error = value
                prefs.edit().putBoolean(KEY_FORCE_401, value).apply()
            }
            KEY_ENABLE_VERBOSE_LOGGING -> {
                enableVerboseLogging = value
                prefs.edit().putBoolean(KEY_ENABLE_VERBOSE_LOGGING, value).apply()
            }
            KEY_MAINTENANCE_MODE -> {
                maintenanceMode = value
                prefs.edit().putBoolean(KEY_MAINTENANCE_MODE, value).apply()
            }
        }
        Log.d(TAG, "Feature flag override set: $key = $value")
    }

    private fun startPolling(context: Context) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                // Poll every 5 minutes or according to configured sync interval (clamped to at least 1 min)
                val intervalMs = (syncIntervalMinutes.coerceAtLeast(1) * 60 * 1000L).coerceAtLeast(60_000L)
                delay(intervalMs)
                fetchConfig(context)
            }
        }
    }
}
