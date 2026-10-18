package com.mosquednd.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NearbyMosqueCacheDao {
    @Query("SELECT * FROM nearby_mosque_cache WHERE cacheKey = :key LIMIT 1")
    fun get(key: String): NearbyMosqueCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entity: NearbyMosqueCacheEntity)

    @Query("DELETE FROM nearby_mosque_cache WHERE cacheKey = :key")
    fun delete(key: String)

    @Query("DELETE FROM nearby_mosque_cache")
    fun clearAll()

    @Query("DELETE FROM nearby_mosque_cache WHERE timestamp < :cutoffTimestamp")
    fun deleteExpired(cutoffTimestamp: Long)
}
