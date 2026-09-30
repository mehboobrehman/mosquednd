package com.mosquednd.data

/**
 * Read-only view of one historical snapshot of a mosque.
 *
 * The full snapshot JSON is also retained ([snapshotJson]) so callers that need
 * the raw serialized state (audit/export tooling) do not need to re-serialize.
 * Property definitions mirror [MosqueHistoryEntity].
 */
data class MosqueHistoryEntry(
    val version: Int,
    val snapshotJson: String,
    val changedBy: String,
    val changeReason: String,
    val createdAt: Long
) {
    /** Parse the stored snapshot back into the domain model — O(1), no replay. */
    fun toMosque(): Mosque? = runCatching {
        MosqueRepository.gson.fromJson(snapshotJson, Mosque::class.java)
    }.getOrNull()
}
