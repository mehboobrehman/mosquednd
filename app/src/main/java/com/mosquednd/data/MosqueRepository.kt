package com.mosquednd.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Repository for managing mosques with Room database storage, atomic versioning,
 * and append-only immutable history tracking.
 *
 * Design Decision: SNAPSHOT-based versioning.
 * Mosque entities are small (~200 bytes JSON), writes are rare user actions, so full
 * snapshots cost negligible storage. Reads of any version are O(1) (no patch-chain replay),
 * restore is simply writing a snapshot as the new current state, and history is
 * crash-safe (no dependency on prior versions).
 */
class MosqueRepository(private val database: MosqueDatabase) {
    private val mosqueDao = database.mosqueDao()
    private val historyDao = database.mosqueHistoryDao()

    constructor(context: Context) : this(MosqueDatabase.getInstance(context))

    fun init(encryptedPrefs: SharedPreferences) {
        migrateFromPrefsIfNeeded(encryptedPrefs)
    }

    private val listMosqueType = TypeToken.getParameterized(List::class.java, Mosque::class.java).type

    private fun migrateFromPrefsIfNeeded(prefs: SharedPreferences) {
        val isMigrated = prefs.getBoolean("room_migration_complete", false)
        if (isMigrated) return

        val json = prefs.getString("mosques_json", null)
        if (!json.isNullOrBlank()) {
            val legacyMosques: List<Mosque>? = runCatching {
                gson.fromJson<List<Mosque>>(json, listMosqueType)
            }.getOrNull()

            if (!legacyMosques.isNullOrEmpty()) {
                runBlocking(Dispatchers.IO) {
                    database.withTransaction {
                        val now = System.currentTimeMillis()
                        legacyMosques.forEach { mosque ->
                            val existing = mosqueDao.getById(mosque.id)
                            if (existing == null) {
                                mosqueDao.upsert(
                                    MosqueEntity.fromMosque(mosque, version = 1, createdAt = now, updatedAt = now)
                                )
                                historyDao.insert(
                                    MosqueHistoryEntity(
                                        entityId = mosque.id,
                                        version = 1,
                                        snapshotJson = gson.toJson(mosque),
                                        changedBy = "local",
                                        changeReason = "migrate_from_prefs",
                                        createdAt = now
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        prefs.edit().putBoolean("room_migration_complete", true).apply()
    }

    suspend fun getAllMosques(): List<Mosque> {
        return mosqueDao.getAll().map { it.toMosque() }
    }

    suspend fun getMosqueById(id: String): Mosque? {
        return mosqueDao.getById(id)?.toMosque()
    }

    suspend fun addMosque(mosque: Mosque, changedBy: String = "local", changeReason: String? = null) {
        database.withTransaction {
            val existing = mosqueDao.getById(mosque.id)
            val maxHistVersion = historyDao.getMaxVersion(mosque.id) ?: 0
            
            val isNew = (existing == null && maxHistVersion == 0)
            val newVersion = if (isNew) 1 else (existing?.version ?: maxHistVersion) + 1
            val reason = changeReason ?: if (isNew) "create" else "update"
            val now = System.currentTimeMillis()
            
            val entity = MosqueEntity.fromMosque(
                mosque, 
                version = newVersion, 
                createdAt = existing?.createdAt ?: now, 
                updatedAt = now
            )
            mosqueDao.upsert(entity)

            val snapshotJson = gson.toJson(mosque)
            val historyEntry = MosqueHistoryEntity(
                entityId = mosque.id,
                version = newVersion,
                snapshotJson = snapshotJson,
                changedBy = changedBy,
                changeReason = reason,
                createdAt = now
            )
            historyDao.insert(historyEntry)
        }
    }

    suspend fun updateMosque(mosque: Mosque, changedBy: String = "local", changeReason: String = "update") {
        addMosque(mosque, changedBy, changeReason)
    }

    suspend fun deleteMosque(mosqueId: String, changedBy: String = "local", changeReason: String = "delete") {
        database.withTransaction {
            val existing = mosqueDao.getById(mosqueId) ?: return@withTransaction
            val maxHistVersion = historyDao.getMaxVersion(mosqueId) ?: existing.version
            val tombstoneVersion = maxHistVersion + 1
            val now = System.currentTimeMillis()
            
            val snapshotJson = gson.toJson(existing.toMosque())
            
            mosqueDao.deleteById(mosqueId)

            val historyEntry = MosqueHistoryEntity(
                entityId = mosqueId,
                version = tombstoneVersion,
                snapshotJson = snapshotJson,
                changedBy = changedBy,
                changeReason = changeReason,
                createdAt = now
            )
            historyDao.insert(historyEntry)
        }
    }

    suspend fun removeMosque(mosqueId: String, changedBy: String = "local", changeReason: String = "delete") {
        deleteMosque(mosqueId, changedBy, changeReason)
    }

    suspend fun restoreVersion(mosqueId: String, targetVersion: Int, changedBy: String = "local", changeReason: String? = null): Boolean {
        return database.withTransaction {
            val historyEntry = historyDao.get(mosqueId, targetVersion) ?: return@withTransaction false
            val restoredMosque = runCatching { gson.fromJson(historyEntry.snapshotJson, Mosque::class.java) }.getOrNull() ?: return@withTransaction false
            
            val existing = mosqueDao.getById(mosqueId)
            val maxHistVersion = historyDao.getMaxVersion(mosqueId) ?: existing?.version ?: 0
            val newVersion = maxHistVersion + 1
            val now = System.currentTimeMillis()
            val reason = changeReason ?: "Restored version $targetVersion"

            val entity = MosqueEntity.fromMosque(
                restoredMosque, 
                version = newVersion, 
                createdAt = existing?.createdAt ?: now, 
                updatedAt = now
            )
            mosqueDao.upsert(entity)

            val newHistory = MosqueHistoryEntity(
                entityId = mosqueId,
                version = newVersion,
                snapshotJson = historyEntry.snapshotJson,
                changedBy = changedBy,
                changeReason = reason,
                createdAt = now
            )
            historyDao.insert(newHistory)
            true
        }
    }

    suspend fun getMosqueHistory(mosqueId: String): List<MosqueHistoryEntry> {
        return historyDao.getByEntity(mosqueId).map { it.toEntry() }
    }

    suspend fun getVersion(mosqueId: String): Int? {
        return mosqueDao.getById(mosqueId)?.version ?: historyDao.getMaxVersion(mosqueId)
    }

    suspend fun getVersionInfo(mosqueId: String): MosqueVersionInfo? {
        val existing = mosqueDao.getById(mosqueId)
        val maxHist = historyDao.getMaxVersion(mosqueId)
        val version = existing?.version ?: maxHist ?: return null
        val history = historyDao.get(mosqueId, version) ?: return null
        
        return MosqueVersionInfo(
            mosqueId = mosqueId,
            version = version,
            createdAt = existing?.createdAt ?: history.createdAt,
            updatedAt = existing?.updatedAt ?: history.createdAt,
            lastChangedBy = history.changedBy,
            lastChangeReason = history.changeReason
        )
    }

    suspend fun getSnapshotJson(mosqueId: String, version: Int): String? {
        return historyDao.get(mosqueId, version)?.snapshotJson
    }

    companion object {
        @Volatile
        private var instance: MosqueRepository? = null

        val gson = Gson()

        fun getInstance(context: Context): MosqueRepository {
            return instance ?: synchronized(this) {
                instance ?: MosqueRepository(context.applicationContext).also { instance = it }
            }
        }

        fun init(context: Context) {
            getInstance(context)
        }

        fun init(encryptedPrefs: SharedPreferences, context: Context) {
            val repo = getInstance(context)
            repo.init(encryptedPrefs)
        }

        @androidx.annotation.VisibleForTesting
        fun setInstanceForTesting(repository: MosqueRepository?) {
            instance = repository
        }

        fun getAllMosques(): List<Mosque> {
            val inst = instance ?: return emptyList()
            return runBlocking(Dispatchers.IO) { inst.getAllMosques() }
        }

        fun getMosqueById(id: String): Mosque? {
            val inst = instance ?: return null
            return runBlocking(Dispatchers.IO) { inst.getMosqueById(id) }
        }

        fun addMosque(mosque: Mosque, changedBy: String = "local", changeReason: String? = null) {
            val inst = instance ?: return
            runBlocking(Dispatchers.IO) { inst.addMosque(mosque, changedBy, changeReason) }
        }

        fun updateMosque(mosque: Mosque, changedBy: String = "local", changeReason: String = "update") {
            val inst = instance ?: return
            runBlocking(Dispatchers.IO) { inst.updateMosque(mosque, changedBy, changeReason) }
        }

        fun deleteMosque(mosqueId: String, changedBy: String = "local", changeReason: String = "delete") {
            val inst = instance ?: return
            runBlocking(Dispatchers.IO) { inst.deleteMosque(mosqueId, changedBy, changeReason) }
        }

        fun removeMosque(mosqueId: String) {
            deleteMosque(mosqueId)
        }

        fun getMosqueHistory(mosqueId: String): List<MosqueHistoryEntry> {
            val inst = instance ?: return emptyList()
            return runBlocking(Dispatchers.IO) { inst.getMosqueHistory(mosqueId) }
        }

        fun getVersion(mosqueId: String): Int? {
            val inst = instance ?: return null
            return runBlocking(Dispatchers.IO) { inst.getVersion(mosqueId) }
        }

        fun getVersionInfo(mosqueId: String): MosqueVersionInfo? {
            val inst = instance ?: return null
            return runBlocking(Dispatchers.IO) { inst.getVersionInfo(mosqueId) }
        }

        fun restoreVersion(mosqueId: String, targetVersion: Int, changedBy: String = "local", changeReason: String? = null): Boolean {
            val inst = instance ?: return false
            return runBlocking(Dispatchers.IO) { inst.restoreVersion(mosqueId, targetVersion, changedBy, changeReason) }
        }

        fun getSnapshotJson(mosqueId: String, version: Int): String? {
            val inst = instance ?: return null
            return runBlocking(Dispatchers.IO) { inst.getSnapshotJson(mosqueId, version) }
        }
    }
}
