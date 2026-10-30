package com.mosquednd.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an AgentLogger log entry.
 */
@Entity(tableName = "agent_logs")
data class AgentLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val level: String,
    val message: String,
    val stack: String?,
    val timestamp: String
)
