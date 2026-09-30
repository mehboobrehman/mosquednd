package com.mosquednd.prayer

data class MethodAngles(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaMinutes: Double = 0.0,       // 0 = use angle, >0 = minutes after Maghrib
    val maghribAngle: Double = 0.0       // 0 = use standard sunset
)

enum class CalculationMethod(val displayName: String, val angles: MethodAngles) {
    MWL(
        "Muslim World League",
        MethodAngles(fajrAngle = 18.0, ishaAngle = 17.0)
    ),
    ISNA(
        "Islamic Society of North America",
        MethodAngles(fajrAngle = 15.0, ishaAngle = 15.0)
    ),
    EGYPT(
        "Egyptian General Authority",
        MethodAngles(fajrAngle = 19.5, ishaAngle = 17.5)
    ),
    UMM_AL_QURA(
        "Umm al-Qura, Makkah",
        MethodAngles(fajrAngle = 18.5, ishaAngle = 0.0, ishaMinutes = 90.0)
    ),
    KARACHI(
        "University of Islamic Sciences, Karachi",
        MethodAngles(fajrAngle = 18.0, ishaAngle = 18.0)
    ),
    TEHRAN(
        "Institute of Geophysics, Tehran",
        MethodAngles(fajrAngle = 17.7, ishaAngle = 14.0, maghribAngle = 4.5)
    ),
    JAFARI(
        "Shia Ithna-Ashari (Jafari)",
        MethodAngles(fajrAngle = 16.0, ishaAngle = 14.0, maghribAngle = 4.0)
    );

    companion object {
        /**
         * Maps a SharedPreferences string value to the corresponding CalculationMethod.
         * Falls back to MWL for unrecognized values (e.g. "dubai" which has no enum).
         */
        fun fromPreferenceValue(value: String): CalculationMethod = when (value) {
            "mwl" -> MWL
            "isna" -> ISNA
            "egypt" -> EGYPT
            "umm_al_qura" -> UMM_AL_QURA
            "karachi" -> KARACHI
            "tehran" -> TEHRAN
            "jafari" -> JAFARI
            else -> {
                android.util.Log.w("CalculationMethod", "Unknown calculation method: $value, falling back to MWL")
                MWL
            }
        }
    }
}
