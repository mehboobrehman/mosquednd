package com.mosquednd.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mosquednd.util.PlaceDetectorCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NearbyMosqueCacheTest {

    private lateinit var context: Context
    private lateinit var database: MosqueDatabase
    private lateinit var dao: NearbyMosqueCacheDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MosqueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.nearbyMosqueCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testPutAndGetCacheEntity() {
        val key = "cache_24.86_67.01_5000"
        val payload = """[{"id":"101","name":"Faisal Mosque","lat":24.86,"lng":67.01}]"""
        val timestamp = System.currentTimeMillis()

        dao.put(NearbyMosqueCacheEntity(key, timestamp, payload))

        val retrieved = dao.get(key)
        assertNotNull(retrieved)
        assertEquals(key, retrieved?.cacheKey)
        assertEquals(timestamp, retrieved?.timestamp)
        assertEquals(payload, retrieved?.jsonPayload)
    }

    @Test
    fun testDeleteAndClearAll() {
        dao.put(NearbyMosqueCacheEntity("key1", 1000L, "data1"))
        dao.put(NearbyMosqueCacheEntity("key2", 2000L, "data2"))

        dao.delete("key1")
        assertNull(dao.get("key1"))
        assertNotNull(dao.get("key2"))

        dao.clearAll()
        assertNull(dao.get("key2"))
    }

    @Test
    fun testDeleteExpired() {
        val now = System.currentTimeMillis()
        val oldTimestamp = now - 100000L
        val recentTimestamp = now - 1000L

        dao.put(NearbyMosqueCacheEntity("old_key", oldTimestamp, "old_data"))
        dao.put(NearbyMosqueCacheEntity("recent_key", recentTimestamp, "recent_data"))

        dao.deleteExpired(cutoffTimestamp = now - 50000L)

        assertNull(dao.get("old_key"))
        assertNotNull(dao.get("recent_key"))
    }

    @Test
    fun testCalculateCacheKey() {
        val key1 = PlaceDetectorCache.calculateCacheKey(24.86123, 67.01456, 5000)
        assertEquals("cache_24.86_67.01_5000", key1)
    }
}
