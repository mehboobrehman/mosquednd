package com.mosquednd.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nearby_mosque_cache")
data class NearbyMosqueCacheEntity(
    @PrimaryKey val cacheKey: String,
    val timestamp: Long,
    val jsonPayload: String
)
