package com.mosquednd.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an AutoDND activity log entry.
 */
@Entity(tableName = "dnd_logs")
data class DNDLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val eventName: String,
    val description: String,
    val mosqueName: String? = null
)
