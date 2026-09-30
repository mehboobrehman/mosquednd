package com.mosquednd.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for the `mosques` table — the *current* state of a mosque.
 *
 * Invariant maintained by [MosqueRepository]: `version` always equals
 * MAX(`mosque_history`.version) for this entity's id. `version` is the
 * monotonically increasing snapshot counter of the entity: 1 after creation,
 * N after the Nth change (create = 1, each update/restore/delete adds +1).
 *
 * `createdAt`/`updatedAt` are epoch millis; they are stamp/audit metadata, NOT
 * part of the [Mosque] domain model (export/import Gson schema is untouched).
 */
@Entity(tableName = "mosques")
data class MosqueEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val geofenceRadius: Float,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** Convert back to the immutable domain model (drops versioning metadata). */
    fun toMosque(): Mosque = Mosque(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        address = address,
        geofenceRadius = geofenceRadius
    )
}