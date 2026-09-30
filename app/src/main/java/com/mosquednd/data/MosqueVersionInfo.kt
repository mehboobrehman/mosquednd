package com.mosquednd.data

/**
 * Version metadata for a single mosque — mirrors the current row's version
 * columns and the latest corresponding history entry.
 */
data class MosqueVersionInfo(
    val mosqueId: String,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastChangedBy: String? = null,
    val lastChangeReason: String? = null
)
