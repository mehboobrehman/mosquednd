package com.mosquednd.data

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing mosques with Room database storage, atomic versioning,
 * and append-only immutable history tracking.
 */
class MosqueRepository(private val database: MosqueDatabase) {
    val gson = Gson()
    private val mosqueDao = database.mosqueDao()
    private val historyDao = database.mosqueHistoryDao()

    constructor(context: Context) : this(MosqueDatabase.build(context))

    private var prefs: android.content.SharedPreferences? = null

    fun init(encryptedPrefs: android.content.SharedPreferences, context: Context) {
        prefs = encryptedPrefs
    }

    private val listMosqueType = TypeToken.getParameterized(List::class.java, Mosque::class.java).type

    suspend fun getAllMosques(): List<Mosque> {
        return mosqueDao.getAll().map { it.toMosque() }
    }

    suspend fun getMosqueById(id: String): Mosque? {
        return mosqueDao.getById(id)?.toMosque()
    }

    suspend fun addMosque(mosque: Mosque, changedBy: String = "system", changeReason: String = "Added mosque") {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = mosqueDao.getById(mosque.id)
            val newVersion = if (existing != null) existing.version + 1 else 1
            val snapshotJson = gson.toJson(mosque)

            val entity = MosqueEntity(
                id = mosque.id,
                name = mosque.name,
                latitude = mosque.latitude,
                longitude = mosque.longitude,
                address = mosque.address,
                geofenceRadius = mosque.geofenceRadius,
                version = newVersion,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )

            mosqueDao.upsert(entity)

            val historyEntry = MosqueHistoryEntity(
                entityId = mosque.id,
                version = newVersion,
                snapshotJson = snapshotJson,
                changedBy = changedBy,
                changeReason = changeReason,
                createdAt = now
            )
            historyDao.insert(historyEntry)
        }
    }

    suspend fun removeMosque(mosqueId: String, changedBy: String = "system", changeReason: String = "Removed mosque") {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = mosqueDao.getById(mosqueId) ?: return@withTransaction
            val newVersion = existing.version + 1
            
            // Tombstone or deleted snapshot json
            val tombstoneMosque = existing.toMosque()
            val snapshotJson = gson.toJson(tombstoneMosque)

            mosqueDao.deleteById(mosqueId)

            val historyEntry = MosqueHistoryEntity(
                entityId = mosqueId,
                version = newVersion,
                snapshotJson = snapshotJson,
                changedBy = changedBy,
                changeReason = changeReason,
                createdAt = now
            )
            historyDao.insert(historyEntry)
        }
    }

    suspend fun getMosqueHistory(mosqueId: String): List<MosqueHistoryEntity> {
        return historyDao.getByEntity(mosqueId)
    }

    suspend fun restoreVersion(mosqueId: String, targetVersion: Int, changedBy: String = "system"): Boolean {
        var restored = false
        database.withTransaction {
            val historyEntry = historyDao.get(mosqueId, targetVersion) ?: return@withTransaction
            val mosque = gson.fromJson(historyEntry.snapshotJson, Mosque::class.java) ?: return@withTransaction
            val existing = mosqueDao.getById(mosqueId)
            val newVersion = (historyDao.getMaxVersion(mosqueId) ?: existing?.version ?: 0) + 1
            val now = System.currentTimeMillis()

            val entity = MosqueEntity(
                id = mosque.id,
                name = mosque.name,
                latitude = mosque.latitude,
                longitude = mosque.longitude,
                address = mosque.address,
                geofenceRadius = mosque.geofenceRadius,
                version = newVersion,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )

            mosqueDao.upsert(entity)

            val restoreHistory = MosqueHistoryEntity(
                entityId = mosqueId,
                version = newVersion,
                snapshotJson = historyEntry.snapshotJson,
                changedBy = changedBy,
                changeReason = "Restored version $targetVersion",
                createdAt = now
            )
            historyDao.insert(restoreHistory)
            restored = true
        }
        return restored
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

        fun init(encryptedPrefs: android.content.SharedPreferences, context: Context) {
            val repo = getInstance(context)
            repo.init(encryptedPrefs, context)
        }

        fun getAllMosques(): List<Mosque> {
            val inst = instance ?: throw IllegalStateException("MosqueRepository not initialized")
            return kotlinx.coroutines.runBlocking { inst.getAllMosques() }
        }

        fun getMosqueById(id: String): Mosque? {
            val inst = instance ?: throw IllegalStateException("MosqueRepository not initialized")
            return kotlinx.coroutines.runBlocking { inst.getMosqueById(id) }
        }

        fun addMosque(mosque: Mosque, changedBy: String = "system", changeReason: String = "Added mosque") {
            val inst = instance ?: throw IllegalStateException("MosqueRepository not initialized")
            kotlinx.coroutines.runBlocking { inst.addMosque(mosque, changedBy, changeReason) }
        }

        fun removeMosque(mosqueId: String, changedBy: String = "system", changeReason: String = "Removed mosque") {
            val inst = instance ?: throw IllegalStateException("MosqueRepository not initialized")
            kotlinx.coroutines.runBlocking { inst.removeMosque(mosqueId, changedBy, changeReason) }
        }
    }
}

object MosqueRepositoryKeys {
    const val MOSQUES_JSON = "mosques_json"
}
