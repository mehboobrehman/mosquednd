package com.mosquednd.whatsnew

import android.content.Context
import android.content.SharedPreferences
import com.mosquednd.AppVersionInfo
import com.mosquednd.BuildConfig

object WhatsNewManager {
    private const val PREF_NAME = "whats_new_prefs"
    private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun shouldShow(context: Context): Boolean {
        val lastSeenVersionCode = getPrefs(context).getInt(KEY_LAST_SEEN_VERSION_CODE, 0)
        val currentVersionCode = getCurrentVersionCode()
        
        // Only show if it's an update (or first install if we want to show on fresh install too)
        // Given requirements, "displays automatically on app updates"
        return currentVersionCode > lastSeenVersionCode
    }

    fun markVersionSeen(context: Context) {
        val currentVersionCode = getCurrentVersionCode()
        getPrefs(context).edit()
            .putInt(KEY_LAST_SEEN_VERSION_CODE, currentVersionCode)
            .apply()
    }

    fun getCurrentVersionCode(): Int {
        // Fallback to BuildConfig if AppVersionInfo doesn't provide it directly
        // Based on app/version.properties read earlier, VERSION_CODE is 12
        return BuildConfig.VERSION_CODE
    }
}
