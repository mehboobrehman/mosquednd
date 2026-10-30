package com.mosquednd.config

import android.content.Context

/**
 * Release stub for RemoteConfigManager.
 */
object RemoteConfigManager {
    val force401Error: Boolean = false
    val enableVerboseLogging: Boolean = false
    val syncIntervalMinutes: Int = 15
    val maintenanceMode: Boolean = false

    fun init(context: Context) {}
    fun fetchConfig(context: Context, onComplete: ((Boolean, String?) -> Unit)? = null) {
        onComplete?.invoke(true, null)
    }
    fun setFeatureFlag(context: Context, key: String, value: Boolean) {}
}
