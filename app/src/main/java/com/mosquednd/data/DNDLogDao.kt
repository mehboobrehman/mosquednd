package com.mosquednd.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for AutoDND activity logs (`dnd_logs` table).
 */
@Dao
interface DNDLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DNDLogEntity)

    @Query("SELECT * FROM dnd_logs ORDER BY timestamp DESC")
    fun getRecentLogsFlow(): Flow<List<DNDLogEntity>>

    @Query("SELECT * FROM dnd_logs ORDER BY timestamp DESC")
    suspend fun getRecentLogs(): List<DNDLogEntity>

    @Query("DELETE FROM dnd_logs")
    suspend fun clearAll()

    /**
     * Deletes a specific log entry by its ID.
     */
    @Query("DELETE FROM dnd_logs WHERE id = :logId")
    suspend fun deleteById(logId: Long)


}
