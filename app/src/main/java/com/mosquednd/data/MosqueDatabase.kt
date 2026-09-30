package com.mosquednd.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for MosqueDND.
 *
 * Two tables:
 *  - `mosques`        — current state of each mosque (one row per mosque).
 *  - `mosque_history` — append-only snapshot/audit trail (see [MosqueHistoryEntity]).
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
    entities = [MosqueEntity::class, MosqueHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MosqueDatabase : RoomDatabase() {
    abstract fun mosqueDao(): MosqueDao
    abstract fun mosqueHistoryDao(): MosqueHistoryDao

    companion object {
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
            ).build()
    }
}
