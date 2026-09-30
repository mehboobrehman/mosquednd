package com.mosquednd.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for the append-only `mosque_history` table.
 *
 * APPEND-ONLY BY DESIGN: this interface exposes ONLY insert + reads. There is no
 * `@Update`, no `@Delete`, and no write query — the row set is immutable once
 * written. Tests assert this invariant via reflection over the interface's
 * declared methods (no update/delete methods exist) and by verifying that the
 * UNIQUE (entityId, version) index rejects a duplicate insert.
 */
@Dao
interface MosqueHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: MosqueHistoryEntity)

    /** All snapshots for an entity, oldest first (version ascending). */
    @Query("SELECT * FROM mosque_history WHERE entityId = :entityId ORDER BY version ASC")
    suspend fun getByEntity(entityId: String): List<MosqueHistoryEntity>

    /** Highest version number recorded for an entity, or null if none. */
    @Query("SELECT MAX(version) FROM mosque_history WHERE entityId = :entityId")
    suspend fun getMaxVersion(entityId: String): Int?

    /** Single snapshot at an exact version, or null if not present. */
    @Query("SELECT * FROM mosque_history WHERE entityId = :entityId AND version = :version")
    suspend fun get(entityId: String, version: Int): MosqueHistoryEntity?
}
