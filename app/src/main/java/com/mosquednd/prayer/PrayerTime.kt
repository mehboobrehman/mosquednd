package com.mosquednd.prayer

import java.util.Calendar

data class PrayerTime(
    val type: PrayerType,
    val time: Calendar,
    val formattedTime: String,
    val isNext: Boolean = false
)
