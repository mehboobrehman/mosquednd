package com.mosquednd.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for current mosque rows. Only full-row writes (insert/update via
 * [MosqueRepository] transactions) are exposed; direct partial deletes are not
 * part of this DAO's surface (deletion flows through [MosqueRepository] so a
 * tombstone history row is always written first).
 */
@Dao
interface MosqueDao {
    @Query("SELECT * FROM mosques WHERE id = :id")
    suspend fun getById(id: String): MosqueEntity?

    @Query("SELECT * FROM mosques")
    suspend fun getAll(): List<MosqueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MosqueEntity)

    @Query("DELETE FROM mosques WHERE id = :id")
    suspend fun deleteById(id: String)
}
