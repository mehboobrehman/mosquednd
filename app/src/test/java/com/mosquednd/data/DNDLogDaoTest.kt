package com.mosquednd.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DNDLogDaoTest {

    private lateinit var db: MosqueDatabase
    private lateinit var dao: DNDLogDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MosqueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dndLogDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetLogs() = runBlocking {
        val log1 = DNDLogEntity(timestamp = 1000L, eventName = "DND ON", description = "Entered mosque radius", mosqueName = "Central Mosque")
        val log2 = DNDLogEntity(timestamp = 2000L, eventName = "DND OFF", description = "Left mosque radius", mosqueName = "Central Mosque")

        dao.insert(log1)
        dao.insert(log2)

        val logs = dao.getRecentLogs()
        assertEquals(2, logs.size)
        // Ordered DESC by timestamp
        assertEquals("DND OFF", logs[0].eventName)
        assertEquals("DND ON", logs[1].eventName)
    }

    @Test
    fun clearAllLogs() = runBlocking {
        val log1 = DNDLogEntity(timestamp = 1000L, eventName = "DND ON", description = "Test")
        dao.insert(log1)
        assertEquals(1, dao.getRecentLogs().size)

        dao.clearAll()
        assertTrue(dao.getRecentLogs().isEmpty())
    }

    @Test
    fun deleteById() = runBlocking {
        val log1 = DNDLogEntity(timestamp = 1000L, eventName = "DND ON", description = "Test 1")
        val log2 = DNDLogEntity(timestamp = 2000L, eventName = "DND OFF", description = "Test 2")
        dao.insert(log1)
        dao.insert(log2)

        val logs = dao.getRecentLogs()
        val idToDelete = logs.first { it.eventName == "DND ON" }.id

        dao.deleteById(idToDelete)

        val remaining = dao.getRecentLogs()
        assertEquals(1, remaining.size)
        assertEquals("DND OFF", remaining[0].eventName)
    }
}
