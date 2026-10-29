package com.mosquednd.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Repository for managing AutoDND activity logs.
 */
object DNDLogRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Inserts an activity log entry asynchronously.
     */
    fun insertLog(context: Context, eventName: String, description: String, mosqueName: String? = null) {
        scope.launch {
            try {
                val db = MosqueDatabase.getInstance(context)
                val entity = DNDLogEntity(
                    timestamp = System.currentTimeMillis(),
                    eventName = eventName,
                    description = description,
                    mosqueName = mosqueName
                )
                db.dndLogDao().insert(entity)
            } catch (e: Exception) {
                android.util.Log.e("DNDLogRepository", "Failed to insert DND log", e)
            }
        }
    }

    /**
     * Suspending function to insert log synchronously (or within coroutine context).
     */
    suspend fun insertLogSuspend(context: Context, eventName: String, description: String, mosqueName: String? = null) {
        try {
            val db = MosqueDatabase.getInstance(context)
            val entity = DNDLogEntity(
                timestamp = System.currentTimeMillis(),
                eventName = eventName,
                description = description,
                mosqueName = mosqueName
            )
            db.dndLogDao().insert(entity)
        } catch (e: Exception) {
            android.util.Log.e("DNDLogRepository", "Failed to insert DND log", e)
        }
    }

    /**
     * Returns a Flow of recent logs ordered by timestamp descending.
     */
    fun getRecentLogsFlow(context: Context): Flow<List<DNDLogEntity>> {
        val db = MosqueDatabase.getInstance(context)
        return db.dndLogDao().getRecentLogsFlow()
    }

    /**
     * Returns a list of recent logs.
     */
    suspend fun getRecentLogs(context: Context): List<DNDLogEntity> {
        val db = MosqueDatabase.getInstance(context)
        return db.dndLogDao().getRecentLogs()
    }

    /**
     * Clears all log entries.
     */
    suspend fun clearAll(context: Context) {
        val db = MosqueDatabase.getInstance(context)
        db.dndLogDao().clearAll()
    }

    /**
     * Deletes a specific log entry by ID.
     */
    suspend fun deleteLog(context: Context, logId: Long) {
        try {
            val db = MosqueDatabase.getInstance(context)
            db.dndLogDao().deleteById(logId)
        } catch (e: Exception) {
            android.util.Log.e("DNDLogRepository", "Failed to delete DND log entry", e)
        }
    }
}
