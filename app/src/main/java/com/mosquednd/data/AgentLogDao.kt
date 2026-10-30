package com.mosquednd.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for AgentLogger logs (`agent_logs` table).
 */
@Dao
interface AgentLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AgentLogEntity)

    @Query("SELECT * FROM agent_logs ORDER BY id ASC")
    suspend fun getAllLogs(): List<AgentLogEntity>

    @Query("DELETE FROM agent_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM agent_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM agent_logs")
    suspend fun getCount(): Int

    @Query("DELETE FROM agent_logs WHERE id NOT IN (SELECT id FROM agent_logs ORDER BY id DESC LIMIT 500)")
    suspend fun trimOldLogs()
}
