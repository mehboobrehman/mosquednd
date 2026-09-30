# Database Migration Plan: SharedPreferences → Room

## Current State

All persistent data is stored as JSON blobs in SharedPreferences via `MosqueDNDApp.prefs`:

| Preference Key | Content | Format |
|---------------|---------|--------|
| `saved_mosques` | List of Mosque objects | JSON array (Gson) |
| `cached_lat`, `cached_lng` | Last known GPS coordinates | Float |
| `has_cached_location` | Sentinel for valid cache | Boolean |
| `calculation_method` | String enum value | "mwl", "isna", etc. |
| `asr_method` | String enum value | "standard", "hanafi" |
| `theme_pref` | Theme string | "light", "dark", "system" |

**Problems with current approach:**
1. **No query capability** — mosque lookup by id/name requires deserializing and iterating the full list
2. **No indexing** — O(n) linear scan for every geofence proximity check
3. **No relational integrity** — duplicate entries, orphaned references between mosques and geofences
4. **No migration story** — any schema change requires manual migration code
5. **Size limits** — SharedPreferences have practical limits (~100KB, ~100 mosques with JSON serialization overhead)
6. **No type safety** — stringly-typed preference keys, Gson deserialization is unchecked

## Migration Target: Room

### Why Room over alternatives

| Criterion | Room | SQLiteOpenHelper | Realm | DataStore |
|-----------|------|-----------------|-------|-----------|
| First-class Android support | ✅ | ✅ | ✅ (winding down) | ✅ |
| Compile-time SQL verification | ✅ | ❌ | N/A | N/A |
| Coroutines/Flow integration | ✅ | Manual | ✅ | ✅ |
| Migration framework | ✅ (versioned) | Manual | ❌ | ❌ |
| Complex queries (JOIN, WHERE) | ✅ | ✅ | Limited | ❌ |
| Type-safe DAOs | ✅ | ❌ | ✅ | ❌ |
| Cross-table transactions | ✅ | ✅ | ✅ | ❌ |
| Dependency size | ~120K methods | Included | ~800K methods | ~50K methods |

**Recommendation: Room (androidx.room:room-ktx)**

### Proposed Schema

#### Table: `mosques`

```sql
CREATE TABLE mosques (
    id TEXT PRIMARY KEY,           -- UUID string, same as current Mosque.id
    name TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    address TEXT,
    place_id TEXT,                 -- OSM place_id for reverse lookup
    geofence_radius_m REAL NOT NULL DEFAULT 200,
    created_at INTEGER NOT NULL,   -- epoch millis
    is_active INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_mosques_lat_lng ON mosques(latitude, longitude);
CREATE INDEX idx_mosques_place_id ON mosques(place_id);
```

**Room Entity:**
```kotlin
@Entity(tableName = "mosques")
data class MosqueEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "address") val address: String?,
    @ColumnInfo(name = "place_id") val placeId: String?,
    @ColumnInfo(name = "geofence_radius_m") val geofenceRadiusM: Float = 200f,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_active") val isActive: Boolean = true
)
```

#### Table: `location_cache`

```sql
CREATE TABLE location_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    accuracy_m REAL,
    timestamp INTEGER NOT NULL       -- epoch millis
);

CREATE INDEX idx_location_cache_ts ON location_cache(timestamp);
```

**Room Entity:**
```kotlin
@Entity(tableName = "location_cache")
data class LocationCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "accuracy_m") val accuracyM: Float?,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
```

#### Table: `settings`

```sql
CREATE TABLE settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
```

**Room Entity:**
```kotlin
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
```

#### Table: `geofence_events` (optional, for debugging)

```sql
CREATE TABLE geofence_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mosque_id TEXT NOT NULL,
    event_type TEXT NOT NULL,         -- "ENTER", "EXIT", "DWELL"
    timestamp INTEGER NOT NULL,
    FOREIGN KEY (mosque_id) REFERENCES mosques(id) ON DELETE CASCADE
);

CREATE INDEX idx_geofence_events_mosque ON geofence_events(mosque_id);
```

### DAO Interfaces

```kotlin
@Dao
interface MosqueDao {
    @Query("SELECT * FROM mosques WHERE is_active = 1 ORDER BY name")
    fun getAllActive(): Flow<List<MosqueEntity>>

    @Query("SELECT * FROM mosques WHERE id = :id")
    suspend fun getById(id: String): MosqueEntity?

    @Query("SELECT * FROM mosques WHERE is_active = 1 AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng")
    suspend fun getByBoundingBox(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<MosqueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mosque: MosqueEntity)

    @Delete
    suspend fun delete(mosque: MosqueEntity)

    @Query("UPDATE mosques SET is_active = 0 WHERE id = :id")
    suspend fun softDelete(id: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingsEntity)

    @Query("DELETE FROM settings")
    suspend fun clearAll()
}
```

### Migration Steps

#### Phase 1: Add Room dependency (Day 1)
```gradle
// build.gradle (app)
dependencies {
    def room_version = "2.6.1"
    implementation "androidx.room:room-runtime:$room_version"
    implementation "androidx.room:room-ktx:$room_version"
    ksp "androidx.room:room-compiler:$room_version"  // or kapt
}

// build.gradle (project-level)
plugins {
    id 'com.google.devtools.ksp' version '1.9.0-1.0.13' apply false
}
```

#### Phase 2: Define entities and DAOs (Day 2)
Create all entity classes and DAO interfaces as described above.
Add `@Database` class with `Room.databaseBuilder()`.

#### Phase 3: Create migration from SharedPreferences (Day 3)
```kotlin
class SharedPrefsToRoomMigration(
    private val app: Application
) : Runnable {

    override fun run() {
        val prefs = MosqueDNDApp.prefs
        
        // Migrate mosques
        val mosquesJson = prefs.getString("saved_mosques", "[]") ?: "[]"
        val mosques: List<Mosque> = Gson().fromJson(mosquesJson, object : TypeToken<List<Mosque>>() {}.type)
        
        val db = MosqueDatabase.getInstance(app)
        for (mosque in mosques) {
            db.mosqueDao().upsert(mosque.toEntity())
        }

        // Migrate location cache
        val hasLocation = prefs.getBoolean("has_cached_location", false)
        if (hasLocation) {
            val lat = prefs.getFloat("cached_lat", 0f).toDouble()
            val lng = prefs.getFloat("cached_lng", 0f).toDouble()
            db.locationCacheDao().insert(LocationCacheEntity(latitude = lat, longitude = lng, timestamp = System.currentTimeMillis()))
        }

        // Migrate settings
        val settingsMap = mapOf(
            "calculation_method" to (prefs.getString("calculation_method", "mwl") ?: "mwl"),
            "asr_method" to (prefs.getString("asr_method", "standard") ?: "standard"),
            "theme_pref" to (prefs.getString("theme_pref", "system") ?: "system")
        )
        for ((key, value) in settingsMap) {
            db.settingsDao().set(SettingsEntity(key = key, value = value))
        }

        // Mark migration complete
        prefs.edit().putBoolean("migrated_to_room", true).apply()
    }
}
```

#### Phase 4: Wrap old SharedPreferences API (Day 4)
Create a `MosqueRepository2` that delegates to Room DAOs instead of SharedPreferences.
Keep the old `MosqueRepository` as a thin wrapper that calls the new one.
Update `MosqueDNDApp` to call the migration on first run.

#### Phase 5: Remove old code (Day 5)
After the migration has run for a release cycle:
- Remove old SharedPreferences read/writes
- Remove Gson serialization of mosque lists
- Delete old `MosqueRepository` and inline to Room DAOs
- Remove `MosqueDNDApp.prefs` migration flag code

### Version Handling

```kotlin
val db = Room.databaseBuilder(
    context,
    MosqueDatabase::class.java,
    "mosquednd.db"
)
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
.fallbackToDestructiveMigration()  // safety net for dev builds
.build()
```

Each schema change gets a `Migration` object with the start/end version.

### Performance Benefits

| Operation | SharedPrefs (JSON) | Room (SQLite) | Improvement |
|-----------|-------------------|---------------|-------------|
| Load 50 mosques | ~5ms (deserialize) | ~1ms (query) | 5x |
| Find mosque by ID | O(n) scan of 50 items | O(1) index lookup | 50x for 50 items |
| Find mosques near (lat,lng) | O(n) scan with distance calc | O(log n) with spatial index | ~10-50x |
| Save 1 mosque | Full list serialization | Single row insert | ~10x |
| Sort by name | O(n log n) sort | Indexed ORDER BY | ~5x |

### Migration Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Data loss** during migration | User loses saved mosques | Copy SharedPrefs → Room first, verify count, then delete old data. Keep backup flag. |
| **Race condition** (read while migrating) | Inconsistent state | Use `synchronized` block. Writes go to SharedPrefs until migration is complete. |
| **Large dataset** (100+ mosques) | Slow migration | Run on background coroutine, show progress notification |
| **Partial migration** (app killed mid-migration) | Missing data | Wrap entire migration in a transaction with `@Transaction` |
| **Increased APK size** | +~200KB | Room adds minimal overhead; Gson removal partially offsets |

### Recommended Timeline

| Phase | Effort | When | Depends On |
|-------|--------|------|-----------|
| Add Room dependency | 1 hour | Sprint A | — |
| Define entities/DAOs | 2 hours | Sprint A | Room dependency |
| Migration script | 3 hours | Sprint B | Entities/DAOs |
| Wrap old API | 4 hours | Sprint B | Migration script |
| Production testing | 2 days | Sprint C | All above |
| Remove old code | 2 hours | Sprint D | Production verification |
