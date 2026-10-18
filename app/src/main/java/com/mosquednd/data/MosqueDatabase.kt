package com.mosquednd.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for MosqueDND.
 *
 * Tables:
 *  - `mosques`             — current state of each mosque (one row per mosque).
 *  - `mosque_history`      — append-only snapshot/audit trail (see [MosqueHistoryEntity]).
 *  - `nearby_mosque_cache` — key-value Room cache for Overpass API responses.
 *
 * SNAPSHOT-based versioning (vs delta/patch chains): a mosque entity is a small
 * ~200-byte JSON object and writes are rare user actions, so storing the full
 * state after every change costs negligible storage. Reads of ANY historical
 * version are O(1) — parse one stored snapshot, no patch-chain replay — and
 * restore == read, so a crashed/corrupt history can never hide earlier state.
 * Delta encoding would only be worth the complexity for large, frequently
 * mutated entities, neither of which is true here.
 *
 * History rows deliberately have no foreign key to `mosques`, so the audit trail
 * survives deletion of the current row (delete writes a tombstone entry instead).
 */
@Database(
    entities = [MosqueEntity::class, MosqueHistoryEntity::class, NearbyMosqueCacheEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MosqueDatabase : RoomDatabase() {
    abstract fun mosqueDao(): MosqueDao
    abstract fun mosqueHistoryDao(): MosqueHistoryDao
    abstract fun nearbyMosqueCacheDao(): NearbyMosqueCacheDao

    companion object {
        @Volatile
        private var INSTANCE: MosqueDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `nearby_mosque_cache` (`cacheKey` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `jsonPayload` TEXT NOT NULL, PRIMARY KEY(`cacheKey`))"
                )
            }
        }

        fun getInstance(context: Context): MosqueDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        /**
         * Build (not yet open) the database. Construction only — actual open
         * happens on first query, so this is safe to call during Direct Boot
         * (before the user unlocks); the getWritableDatabase path inside Room
         * still runs on our dedicated single-thread executor.
         */
        fun build(context: Context): MosqueDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MosqueDatabase::class.java,
                "mosquednd.db"
            )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }
}
