package com.mosquednd.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MosqueRepositoryTest {
    private lateinit var db: MosqueDatabase
    private lateinit var repository: MosqueRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MosqueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MosqueRepository(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testAddAndGetMosque() = runBlocking {
        val mosque = Mosque(
            id = "test-1",
            name = "Test Mosque",
            latitude = 24.8607,
            longitude = 67.0011,
            address = "Karachi",
            geofenceRadius = 100f
        )

        repository.addMosque(mosque, changedBy = "admin", changeReason = "Initial create")

        val mosques = repository.getAllMosques()
        assertEquals(1, mosques.size)
        assertEquals("Test Mosque", mosques[0].name)

        val history = repository.getMosqueHistory("test-1")
        assertEquals(1, history.size)
        assertEquals(1, history[0].version)
        assertEquals("admin", history[0].changedBy)
        assertEquals("Initial create", history[0].changeReason)
    }

    @Test
    fun testVersionIncrementAndHistoryAppend() = runBlocking {
        val mosque1 = Mosque(
            id = "test-2",
            name = "Madani Mosque",
            latitude = 24.0,
            longitude = 67.0,
            address = "Address 1",
            geofenceRadius = 50f
        )
        repository.addMosque(mosque1, changeReason = "v1")

        val mosque2 = Mosque(
            id = "test-2",
            name = "Madani Mosque Updated",
            latitude = 24.1,
            longitude = 67.1,
            address = "Address 2",
            geofenceRadius = 75f
        )
        repository.addMosque(mosque2, changeReason = "v2")

        val history = repository.getMosqueHistory("test-2")
        assertEquals(2, history.size)
        assertEquals(1, history[0].version)
        assertEquals(2, history[1].version)
        assertEquals("v1", history[0].changeReason)
        assertEquals("v2", history[1].changeReason)

        val current = repository.getMosqueById("test-2")
        assertNotNull(current)
        assertEquals("Madani Mosque Updated", current?.name)
        assertEquals(75f, current?.geofenceRadius)
    }

    @Test
    fun testRestoreVersion() = runBlocking {
        val mosque1 = Mosque(id = "test-3", name = "Jamia Masjid v1", latitude = 0.0, longitude = 0.0, address = "A", geofenceRadius = 10f)
        repository.addMosque(mosque1, changeReason = "First")

        val mosque2 = Mosque(id = "test-3", name = "Jamia Masjid v2", latitude = 1.0, longitude = 1.0, address = "B", geofenceRadius = 20f)
        repository.addMosque(mosque2, changeReason = "Second")

        val success = repository.restoreVersion("test-3", 1, changedBy = "tester")
        assertTrue(success)

        val current = repository.getMosqueById("test-3")
        assertNotNull(current)
        assertEquals("Jamia Masjid v1", current?.name)

        val history = repository.getMosqueHistory("test-3")
        assertEquals(3, history.size)
        assertEquals(3, history[2].version)
        assertEquals("Restored version 1", history[2].changeReason)
        assertEquals("tester", history[2].changedBy)
    }

    @Test
    fun testRemoveMosqueTombstone() = runBlocking {
        val mosque = Mosque(id = "test-4", name = "Temporary Mosque", latitude = 0.0, longitude = 0.0, address = "T", geofenceRadius = 10f)
        repository.addMosque(mosque, changeReason = "Add")

        repository.removeMosque("test-4", changedBy = "admin", changeReason = "Delete")

        val mosques = repository.getAllMosques()
        assertTrue(mosques.isEmpty())

        val history = repository.getMosqueHistory("test-4")
        assertEquals(2, history.size)
        assertEquals(2, history[1].version)
        assertEquals("Delete", history[1].changeReason)
    }
}
