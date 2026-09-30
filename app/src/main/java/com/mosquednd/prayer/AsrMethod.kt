package com.mosquednd.prayer

enum class AsrMethod(val displayName: String, val shadowFactor: Double) {
    SHAFII("Shafi'i / Standard", 1.0),
    HANAFI("Hanafi", 2.0);

    companion object {
        /**
         * Maps a SharedPreferences string value to the corresponding AsrMethod.
         * Falls back to SHAFII for unrecognized values.
         */
        fun fromPreferenceValue(value: String): AsrMethod = when (value) {
            "standard", "shafii" -> SHAFII
            "hanafi" -> HANAFI
            else -> {
                android.util.Log.w("AsrMethod", "Unknown Asr method: $value, falling back to SHAFII")
                SHAFII
            }
        }
    }
}
