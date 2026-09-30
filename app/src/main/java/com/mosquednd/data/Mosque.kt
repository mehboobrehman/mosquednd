package com.mosquednd.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Mosque(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val geofenceRadius: Float = 50f
) : Parcelable {
    companion object {
        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }
}
