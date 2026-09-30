package com.mosquednd.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.Update
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Unit tests for [MosqueRepository], [MosqueDatabase], snapshot-based entity versioning,
 * append-only history tracking, SharedPreferences migration, and sync companion APIs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MosqueRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: MosqueDatabase
    private lateinit var repository: MosqueRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MosqueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MosqueRepository(database)
        MosqueRepository.setInstanceForTesting(repository)
    }

    @After
    fun tearDown() {
        MosqueRepository.setInstanceForTesting(null)
        database.close()
    }

    @Test
    fun testCreateMosqueCreatesVersion1AndHistory() = runBlocking {
        val mosque = Mosque(
            id = UUID.randomUUID().toString(),
            name = "Masjid Al-Haram",
            latitude = 21.4225,
            longitude = 39.8262,
            address = "Makkah",
            geofenceRadius = 100f
        )

        repository.addMosque(mosque, changedBy = "user1")

        val fetched = repository.getMosqueById(mosque.id)
        assertNotNull(fetched)
        assertEquals(mosque.id, fetched!!.id)
        assertEquals("Masjid Al-Haram", fetched.name)

        val version = repository.getVersion(mosque.id)
        assertEquals(1, version)

        val versionInfo = repository.getVersionInfo(mosque.id)
        assertNotNull(versionInfo)
        assertEquals(1, versionInfo!!.version)
        assertEquals("user1", versionInfo.lastChangedBy)
        assertEquals("create", versionInfo.lastChangeReason)

        val history = repository.getMosqueHistory(mosque.id)
        assertEquals(1, history.size)
        assertEquals(1, history[0].version)
        assertEquals("create", history[0].changeReason)
        assertEquals("user1", history[0].changedBy)
        assertEquals("Masjid Al-Haram", history[0].toMosque()?.name)
    }

    @Test
    fun testUpdateMosqueIncrementsVersionMonotonically() = runBlocking {
        val mosqueId = UUID.randomUUID().toString()
        val initialMosque = Mosque(
            id = mosqueId,
            name = "Masjid An-Nabawi",
            latitude = 24.5247,
            longitude = 39.5692,
            address = "Madinah",
            geofenceRadius = 50f
        )

        repository.addMosque(initialMosque, changedBy = "admin")

        val updatedMosque = initialMosque.copy(
            name = "Prophet's Mosque (Al-Masjid an-Nabawi)",
            geofenceRadius = 120f
        )
        repository.updateMosque(updatedMosque, changedBy = "admin", changeReason = "update_name_radius")

        val fetched = repository.getMosqueById(mosqueId)
        assertNotNull(fetched)
        assertEquals("Prophet's Mosque (Al-Masjid an-Nabawi)", fetched!!.name)
        assertEquals(120f, fetched.geofenceRadius, 0.01f)

        val version = repository.getVersion(mosqueId)
        assertEquals(2, version)

        val thirdMosque = updatedMosque.copy(address = "Al Haram, Medina 42311")
        repository.updateMosque(thirdMosque, changedBy = "editor", changeReason = "update_address")

        val version3 = repository.getVersion(mosqueId)
        assertEquals(3, version3)

        val history = repository.getMosqueHistory(mosqueId)
        assertEquals(3, history.size)
        assertEquals(1, history[0].version)
        assertEquals("create", history[0].changeReason)
        assertEquals(2, history[1].version)
        assertEquals("update_name_radius", history[1].changeReason)
        assertEquals(3, history[2].version)
        assertEquals("update_address", history[2].changeReason)
        assertEquals("editor", history[2].changedBy)
    }

    @Test
    fun testHistoryDaoIsAppendOnlyByDesign() {
        val methods = MosqueHistoryDao::class.java.declaredMethods
        for (method in methods) {
            assertFalse(
                "MosqueHistoryDao must not have @Update methods (append-only audit trail)",
                method.isAnnotationPresent(Update::class.java)
            )
            assertFalse(
                "MosqueHistoryDao must not have @Delete methods (append-only audit trail)",
                method.isAnnotationPresent(Delete::class.java)
            )
            assertFalse(
                "MosqueHistoryDao method name must not contain 'delete'",
                method.name.lowercase().contains("delete")
            )
            assertFalse(
                "MosqueHistoryDao method name must not contain 'update'",
                method.name.lowercase().contains("update")
            )
        }
    }

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun testDuplicateEntityIdAndVersionRejected() = runBlocking {
        val historyDao = database.mosqueHistoryDao()
        val entry1 = MosqueHistoryEntity(
            entityId = "m1",
            version = 1,
            snapshotJson = "{}",
            changedBy = "user",
            changeReason = "create",
            createdAt = System.currentTimeMillis()
        )
        val entry2 = MosqueHistoryEntity(
            entityId = "m1",
            version = 1,
            snapshotJson = "{}",
            changedBy = "user",
            changeReason = "update",
            createdAt = System.currentTimeMillis()
        )
        historyDao.insert(entry1)
        historyDao.insert(entry2)
    }

    @Test
    fun testRestoreVersionCreatesNewVersionPreservingHistory() = runBlocking {
        val mosqueId = UUID.randomUUID().toString()
        val original = Mosque(
            id = mosqueId,
            name = "Original Name",
            latitude = 25.0,
            longitude = 67.0,
            address = "Original Address",
            geofenceRadius = 50f
        )
        repository.addMosque(original)

        val modified = original.copy(name = "Modified Name", geofenceRadius = 80f)
        repository.updateMosque(modified)

        assertEquals(2, repository.getVersion(mosqueId))

        // Restore to version 1
        val restored = repository.restoreVersion(
            mosqueId = mosqueId,
            targetVersion = 1,
            changedBy = "admin",
            changeReason = "revert_accidental_edit"
        )
        assertTrue(restored)

        val current = repository.getMosqueById(mosqueId)
        assertNotNull(current)
        assertEquals("Original Name", current!!.name)
        assertEquals(50f, current.geofenceRadius, 0.01f)

        // Version should be 3 (maxVersion + 1)
        assertEquals(3, repository.getVersion(mosqueId))

        val history = repository.getMosqueHistory(mosqueId)
        assertEquals(3, history.size)
        assertEquals(1, history[0].version)
        assertEquals("Original Name", history[0].toMosque()?.name)
        assertEquals(2, history[1].version)
        assertEquals("Modified Name", history[1].toMosque()?.name)
        assertEquals(3, history[2].version)
        assertEquals("Original Name", history[2].toMosque()?.name)
        assertEquals("revert_accidental_edit", history[2].changeReason)
    }

    @Test
    fun testDeleteMosqueCreatesTombstoneInHistory() = runBlocking {
        val mosqueId = UUID.randomUUID().toString()
        val mosque = Mosque(
            id = mosqueId,
            name = "To Be Deleted",
            latitude = 24.86,
            longitude = 67.0,
            address = "Karachi",
            geofenceRadius = 60f
        )
        repository.addMosque(mosque)
        assertEquals(1, repository.getVersion(mosqueId))

        repository.deleteMosque(mosqueId, changedBy = "user_del", changeReason = "user_removed")

        // Current state row must be deleted
        assertNull(repository.getMosqueById(mosqueId))
        assertTrue(repository.getAllMosques().none { it.id == mosqueId })

        // History must survive and contain tombstone as version 2
        val history = repository.getMosqueHistory(mosqueId)
        assertEquals(2, history.size)
        assertEquals(1, history[0].version)
        assertEquals("create", history[0].changeReason)
        assertEquals(2, history[1].version)
        assertEquals("user_removed", history[1].changeReason)
        assertEquals("user_del", history[1].changedBy)
        assertEquals("To Be Deleted", history[1].toMosque()?.name)

        // Max version in history is 2
        assertEquals(2, repository.getVersion(mosqueId))
    }

    @Test
    fun testSharedPreferencesMigration() = runBlocking {
        val prefs = context.getSharedPreferences("test_mosque_repo_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val legacyList = listOf(
            Mosque(
                id = UUID.randomUUID().toString(),
                name = "Legacy Mosque 1",
                latitude = 24.8607,
                longitude = 67.0011,
                address = "Legacy Address 1",
                geofenceRadius = 50f
            ),
            Mosque(
                id = UUID.randomUUID().toString(),
                name = "Legacy Mosque 2",
                latitude = 24.8711,
                longitude = 67.0222,
                address = "Legacy Address 2",
                geofenceRadius = 75f
            )
        )

        val json = MosqueRepository.gson.toJson(legacyList)
        prefs.edit()
            .putString("mosques_json", json)
            .putBoolean("room_migration_complete", false)
            .commit()

        repository.init(prefs)

        val migrated = repository.getAllMosques()
        assertEquals(2, migrated.size)
        assertTrue(migrated.any { it.name == "Legacy Mosque 1" })
        assertTrue(migrated.any { it.name == "Legacy Mosque 2" })

        for (m in migrated) {
            val version = repository.getVersion(m.id)
            assertEquals(1, version)
            val history = repository.getMosqueHistory(m.id)
            assertEquals(1, history.size)
            assertEquals("migrate_from_prefs", history[0].changeReason)
            assertEquals("local", history[0].changedBy)
        }

        // Sentinel must be set
        assertTrue(prefs.getBoolean("room_migration_complete", false))
        // Legacy JSON must NOT be deleted
        assertEquals(json, prefs.getString("mosques_json", null))

        // Re-running init must be a no-op
        repository.init(prefs)
        assertEquals(2, repository.getAllMosques().size)
    }

    @Test
    fun testSynchronousCompanionMethodsWorkWithoutViolations() {
        val mosque = Mosque(
            id = UUID.randomUUID().toString(),
            name = "Sync Mosque",
            latitude = 24.9,
            longitude = 67.1,
            address = "Sync Address",
            geofenceRadius = 45f
        )

        MosqueRepository.addMosque(mosque)

        val all = MosqueRepository.getAllMosques()
        assertEquals(1, all.size)
        assertEquals("Sync Mosque", all[0].name)

        val single = MosqueRepository.getMosqueById(mosque.id)
        assertNotNull(single)
        assertEquals("Sync Mosque", single!!.name)

        val updated = mosque.copy(name = "Updated Sync Mosque")
        MosqueRepository.updateMosque(updated)
        assertEquals(2, MosqueRepository.getVersion(mosque.id))

        val history = MosqueRepository.getMosqueHistory(mosque.id)
        assertEquals(2, history.size)

        val snapshot = MosqueRepository.getSnapshotJson(mosque.id, 1)
        assertNotNull(snapshot)
        assertTrue(snapshot!!.contains("Sync Mosque"))

        val versionInfo = MosqueRepository.getVersionInfo(mosque.id)
        assertNotNull(versionInfo)
        assertEquals(2, versionInfo!!.version)

        val restored = MosqueRepository.restoreVersion(mosque.id, 1)
        assertTrue(restored)
        assertEquals("Sync Mosque", MosqueRepository.getMosqueById(mosque.id)?.name)
        assertEquals(3, MosqueRepository.getVersion(mosque.id))

        MosqueRepository.removeMosque(mosque.id)
        assertNull(MosqueRepository.getMosqueById(mosque.id))
        assertEquals(0, MosqueRepository.getAllMosques().size)
    }

    @Test
    fun testDirectBootSafetyWhenUninitialized() {
        MosqueRepository.setInstanceForTesting(null)

        // Must not crash with IllegalStateException
        val mosques = MosqueRepository.getAllMosques()
        assertTrue(mosques.isEmpty())

        val single = MosqueRepository.getMosqueById("some-id")
        assertNull(single)

        val history = MosqueRepository.getMosqueHistory("some-id")
        assertTrue(history.isEmpty())

        val version = MosqueRepository.getVersion("some-id")
        assertNull(version)

        val versionInfo = MosqueRepository.getVersionInfo("some-id")
        assertNull(versionInfo)

        val restored = MosqueRepository.restoreVersion("some-id", 1)
        assertFalse(restored)

        val snapshot = MosqueRepository.getSnapshotJson("some-id", 1)
        assertNull(snapshot)
    }
}
