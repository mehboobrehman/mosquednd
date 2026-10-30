package com.mosquednd.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Release stub for AgentLogEntity.
 */
@Entity(tableName = "agent_logs")
data class AgentLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val level: String = "",
    val message: String = "",
    val stack: String? = null,
    val timestamp: String = ""
)
