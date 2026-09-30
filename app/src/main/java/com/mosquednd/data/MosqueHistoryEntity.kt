package com.mosquednd.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row in the `mosque_history` table — one immutable snapshot of a mosque.
 *
 * The table is APPEND-ONLY: rows are never updated or deleted. [MosqueRepository]
 * only inserts through [MosqueHistoryDao], write methods beyond insert simply do
 * not exist (immutability enforced by DAO API design). Rows survive even after
 * the current mosque row is deleted (deliberately NO foreign key to `mosques`):
 * deletion writes a tombstone history row instead.
 *
 * `snapshotJson` is the entity state AFTER the change that produced this version,
 * serialized with the repository's existing Gson instance — restore of any
 * version is therefore O(1): parse the snapshot, no patch-chain replay.
 */
@Entity(
    tableName = "mosque_history",
    indices = [Index(value = ["entityId", "version"], unique = true)]
)
data class MosqueHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityId: String,
    val version: Int,
    val snapshotJson: String,
    val changedBy: String,
    val changeReason: String,
    val createdAt: Long
) {
    fun toEntry(): MosqueHistoryEntry = MosqueHistoryEntry(
        version = version,
        snapshotJson = snapshotJson,
        changedBy = changedBy,
        changeReason = changeReason,
        createdAt = createdAt
    )
}