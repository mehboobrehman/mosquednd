package com.mosquednd.theme

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.mosquednd.MosqueDNDApp

object AppTheme {

    private const val TAG = "AppTheme"

    /**
     * Reads the saved theme preference from SharedPreferences and applies it
     * via AppCompatDelegate.setDefaultNightMode().
     *
     * Maps:
     * - "light" -> MODE_NIGHT_NO
     * - "dark"  -> MODE_NIGHT_YES
     * - "system" -> MODE_NIGHT_FOLLOW_SYSTEM (also the fallback default)
     */
    fun apply(context: Context) {
        val theme = getCurrentTheme()
        Log.d(TAG, "Applying theme: $theme")

        when (theme) {
            MosqueDNDApp.THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Log.d(TAG, "Set night mode: MODE_NIGHT_NO")
            }
            MosqueDNDApp.THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Log.d(TAG, "Set night mode: MODE_NIGHT_YES")
            }
            MosqueDNDApp.THEME_SYSTEM -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                Log.d(TAG, "Set night mode: MODE_NIGHT_FOLLOW_SYSTEM")
            }
            else -> {
                Log.w(TAG, "Unknown theme preference: $theme, falling back to system")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    /**
     * Persists the chosen theme string to SharedPreferences and re-applies it.
     *
     * @param theme one of MosqueDNDApp.THEME_LIGHT, THEME_DARK, or THEME_SYSTEM
     */
    fun setThemePreference(context: Context, theme: String) {
        Log.d(TAG, "Setting theme preference: $theme")
        MosqueDNDApp.prefs.edit()
            .putString(MosqueDNDApp.PREFS_THEME, theme)
            .apply()
        apply(context)
    }

    /**
     * Returns the currently saved theme preference.
     * Defaults to "system" if no preference has ever been saved.
     */
    fun getCurrentTheme(): String {
        val theme = MosqueDNDApp.prefs.getString(
            MosqueDNDApp.PREFS_THEME,
            MosqueDNDApp.THEME_SYSTEM
        ) ?: MosqueDNDApp.THEME_SYSTEM
        Log.d(TAG, "Current theme: $theme")
        return theme
    }
}
