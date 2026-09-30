package com.mosquednd.prayer

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PrayerAlarmPrefs using an in-memory SharedPreferences implementation.
 */
class PrayerAlarmPrefsTest {

    private lateinit var testPrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        testPrefs = InMemorySharedPreferences()
        PrayerAlarmPrefs.initForTest(testPrefs)
    }

    // --- Offset Tests ---

    @Test
    fun `default offset is 0 for all prayer types`() {
        for (type in PrayerType.entries) {
            assertEquals("Default offset for $type should be 0", 0, PrayerAlarmPrefs.getOffset(type))
        }
    }

    @Test
    fun `setOffset and getOffset round-trip correctly`() {
        PrayerAlarmPrefs.setOffset(PrayerType.FAJR, 15)
        assertEquals(15, PrayerAlarmPrefs.getOffset(PrayerType.FAJR))

        PrayerAlarmPrefs.setOffset(PrayerType.DHUHR, -10)
        assertEquals(-10, PrayerAlarmPrefs.getOffset(PrayerType.DHUHR))

        PrayerAlarmPrefs.setOffset(PrayerType.ISHA, 30)
        assertEquals(30, PrayerAlarmPrefs.getOffset(PrayerType.ISHA))
    }

    @Test
    fun `offset values are clamped to -60 when below range`() {
        PrayerAlarmPrefs.setOffset(PrayerType.FAJR, -70)
        assertEquals(-60, PrayerAlarmPrefs.getOffset(PrayerType.FAJR))
    }

    @Test
    fun `offset values are clamped to +60 when above range`() {
        PrayerAlarmPrefs.setOffset(PrayerType.MAGHRIB, 70)
        assertEquals(60, PrayerAlarmPrefs.getOffset(PrayerType.MAGHRIB))
    }

    @Test
    fun `offset boundary values of -60 and +60 are accepted`() {
        PrayerAlarmPrefs.setOffset(PrayerType.FAJR, -60)
        assertEquals(-60, PrayerAlarmPrefs.getOffset(PrayerType.FAJR))

        PrayerAlarmPrefs.setOffset(PrayerType.ISHA, 60)
        assertEquals(60, PrayerAlarmPrefs.getOffset(PrayerType.ISHA))
    }

    // --- Hijri Date Offset Tests ---

    @Test
    fun `hijri date offset default is 0`() {
        assertEquals(0, PrayerAlarmPrefs.getHijriDateOffset())
    }

    @Test
    fun `hijri date offset set and get round-trip`() {
        testPrefs.edit().putString("hijri_date_offset", "1").apply()
        assertEquals(1, PrayerAlarmPrefs.getHijriDateOffset())
    }

    @Test
    fun `hijri date offset out of range high clamped to +2`() {
        testPrefs.edit().putString("hijri_date_offset", "5").apply()
        assertEquals(2, PrayerAlarmPrefs.getHijriDateOffset())
    }

    @Test
    fun `hijri date offset out of range low clamped to -2`() {
        testPrefs.edit().putString("hijri_date_offset", "-5").apply()
        assertEquals(-2, PrayerAlarmPrefs.getHijriDateOffset())
    }

    // --- Alarm Toggle Tests ---

    @Test
    fun `alarm enabled defaults to true for all prayer types`() {
        for (type in PrayerType.entries) {
            assertTrue("Default alarm for $type should be true", PrayerAlarmPrefs.isAlarmEnabled(type))
        }
    }

    @Test
    fun `setAlarmEnabled and isAlarmEnabled round-trip correctly`() {
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.FAJR, false)
        assertFalse(PrayerAlarmPrefs.isAlarmEnabled(PrayerType.FAJR))

        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.FAJR, true)
        assertTrue(PrayerAlarmPrefs.isAlarmEnabled(PrayerType.FAJR))
    }

    @Test
    fun `alarm toggle isolated per prayer type`() {
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.FAJR, false)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.DHUHR, true)

        assertFalse(PrayerAlarmPrefs.isAlarmEnabled(PrayerType.FAJR))
        assertTrue(PrayerAlarmPrefs.isAlarmEnabled(PrayerType.DHUHR))
    }

    // --- toOffsetsMap Tests ---

    @Test
    fun `toOffsetsMap returns all 6 prayer types with correct values`() {
        PrayerAlarmPrefs.setOffset(PrayerType.FAJR, 10)
        PrayerAlarmPrefs.setOffset(PrayerType.ISHA, -5)

        val map = PrayerAlarmPrefs.toOffsetsMap()
        assertEquals(6, map.size)

        // All types present
        for (type in PrayerType.entries) {
            assertTrue("Map should contain $type", map.containsKey(type))
        }

        // Specific values
        assertEquals(10, map[PrayerType.FAJR])
        assertEquals(-5, map[PrayerType.ISHA])
        assertEquals(0, map[PrayerType.SUNRISE])
    }

    // --- getEnabledPrayerTypes Tests ---

    @Test
    fun `getEnabledPrayerTypes excludes Sunrise`() {
        val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes()
        assertFalse("Sunrise should not be in enabled types", enabledTypes.contains(PrayerType.SUNRISE))
    }

    @Test
    fun `getEnabledPrayerTypes returns only enabled types`() {
        // Disable Dhuhr and Asr
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.DHUHR, false)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.ASR, false)

        val enabledTypes = PrayerAlarmPrefs.getEnabledPrayerTypes()
        assertFalse(enabledTypes.contains(PrayerType.DHUHR))
        assertFalse(enabledTypes.contains(PrayerType.ASR))
        assertTrue(enabledTypes.contains(PrayerType.FAJR))
        assertTrue(enabledTypes.contains(PrayerType.MAGHRIB))
        assertTrue(enabledTypes.contains(PrayerType.ISHA))
    }

    // --- Master Toggle Tests ---

    @Test
    fun `master alarm defaults to true`() {
        assertTrue(PrayerAlarmPrefs.isMasterAlarmEnabled())
    }

    @Test
    fun `master alarm round-trip`() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        assertFalse(PrayerAlarmPrefs.isMasterAlarmEnabled())

        PrayerAlarmPrefs.setMasterAlarmEnabled(true)
        assertTrue(PrayerAlarmPrefs.isMasterAlarmEnabled())
    }

    // --- Custom Adhan URI Tests ---

    @Test
    fun `custom adhan uri defaults to null`() {
        assertNull(PrayerAlarmPrefs.getCustomAdhanUri())
    }

    @Test
    fun `custom adhan uri set and get round-trip`() {
        PrayerAlarmPrefs.setCustomAdhanUri("content://media/internal/audio/alarms/123")
        assertEquals("content://media/internal/audio/alarms/123", PrayerAlarmPrefs.getCustomAdhanUri())
    }

    @Test
    fun `custom adhan uri can be cleared with null`() {
        PrayerAlarmPrefs.setCustomAdhanUri("content://something")
        PrayerAlarmPrefs.setCustomAdhanUri(null)
        assertNull(PrayerAlarmPrefs.getCustomAdhanUri())
    }

    @Test
    fun `custom adhan uri returns null for blank string`() {
        PrayerAlarmPrefs.setCustomAdhanUri("")
        assertNull(PrayerAlarmPrefs.getCustomAdhanUri())
    }

    @Test
    fun `custom adhan uri returns null for whitespace`() {
        PrayerAlarmPrefs.setCustomAdhanUri("   ")
        assertNull(PrayerAlarmPrefs.getCustomAdhanUri())
    }

    // ──────────────────────────────────────────────
    // DND Duration Tests
    // ──────────────────────────────────────────────

    @Test
    fun `dnd duration defaults to 10 for non-sunrise prayers`() {
        for (type in PrayerType.entries) {
            if (type == PrayerType.SUNRISE) continue
            assertEquals("Default DND duration for $type should be 10", 10, PrayerAlarmPrefs.getDNDDuration(type))
        }
    }

    @Test
    fun `setDNDDuration and getDNDDuration round-trip for valid values`() {
        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 15)
        assertEquals(15, PrayerAlarmPrefs.getDNDDuration(PrayerType.FAJR))

        PrayerAlarmPrefs.setDNDDuration(PrayerType.DHUHR, 30)
        assertEquals(30, PrayerAlarmPrefs.getDNDDuration(PrayerType.DHUHR))

        PrayerAlarmPrefs.setDNDDuration(PrayerType.ISHA, 60)
        assertEquals(60, PrayerAlarmPrefs.getDNDDuration(PrayerType.ISHA))
    }

    @Test
    fun `dnd duration clamps values to nearest valid preset`() {
        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 7)   // <= 7 → 5
        assertEquals(5, PrayerAlarmPrefs.getDNDDuration(PrayerType.FAJR))

        PrayerAlarmPrefs.setDNDDuration(PrayerType.DHUHR, 12)  // <= 12 → 10
        assertEquals(10, PrayerAlarmPrefs.getDNDDuration(PrayerType.DHUHR))

        PrayerAlarmPrefs.setDNDDuration(PrayerType.ASR, 45)    // <= 45 → 30
        assertEquals(30, PrayerAlarmPrefs.getDNDDuration(PrayerType.ASR))
    }

    @Test
    fun `dnd duration is isolated per prayer type`() {
        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 15)
        PrayerAlarmPrefs.setDNDDuration(PrayerType.DHUHR, 30)
        // Set Asr to non-default to ensure isolation
        PrayerAlarmPrefs.setDNDDuration(PrayerType.ASR, 5)

        assertEquals(15, PrayerAlarmPrefs.getDNDDuration(PrayerType.FAJR))
        assertEquals(30, PrayerAlarmPrefs.getDNDDuration(PrayerType.DHUHR))
        assertEquals(5, PrayerAlarmPrefs.getDNDDuration(PrayerType.ASR))
        // Unchanged types stay at 10
        assertEquals(10, PrayerAlarmPrefs.getDNDDuration(PrayerType.MAGHRIB))
        assertEquals(10, PrayerAlarmPrefs.getDNDDuration(PrayerType.ISHA))
    }

    @Test
    fun `setDNDDurationRaw stores non-standard values without clamping`() {
        PrayerAlarmPrefs.setDNDDurationRaw(PrayerType.FAJR, 22)
        assertEquals(22, PrayerAlarmPrefs.getDNDDuration(PrayerType.FAJR))

        PrayerAlarmPrefs.setDNDDurationRaw(PrayerType.DHUHR, 999)
        assertEquals(999, PrayerAlarmPrefs.getDNDDuration(PrayerType.DHUHR))
    }

    @Test
    fun `setDNDDurationRaw clamps values to 1-999 range`() {
        PrayerAlarmPrefs.setDNDDurationRaw(PrayerType.FAJR, 0)
        assertEquals(1, PrayerAlarmPrefs.getDNDDuration(PrayerType.FAJR))

        PrayerAlarmPrefs.setDNDDurationRaw(PrayerType.DHUHR, 1000)
        assertEquals(999, PrayerAlarmPrefs.getDNDDuration(PrayerType.DHUHR))
    }

    @Test
    fun `isDNDDurationCustom returns true for non-standard values`() {
        PrayerAlarmPrefs.setDNDDurationRaw(PrayerType.FAJR, 22)
        assertTrue(PrayerAlarmPrefs.isDNDDurationCustom(PrayerType.FAJR))
    }

    @Test
    fun `isDNDDurationCustom returns false for standard preset values`() {
        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 15)
        assertFalse(PrayerAlarmPrefs.isDNDDurationCustom(PrayerType.FAJR))

        PrayerAlarmPrefs.setDNDDuration(PrayerType.DHUHR, 60)
        assertFalse(PrayerAlarmPrefs.isDNDDurationCustom(PrayerType.DHUHR))
    }

    @Test
    fun `setDNDDuration only fires listener once for the correct key`() {
        // This test verifies that setDNDDuration is a pure SharedPreferences
        // operation with no cascading side effects to other preferences or APIs.
        val capturedKeys = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            capturedKeys.add(key)
        }
        testPrefs.registerOnSharedPreferenceChangeListener(listener)

        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 30)

        // Verify listener fired exactly once
        assertEquals(1, capturedKeys.size)
        // Verify the correct key was notified
        val key = capturedKeys[0] ?: throw AssertionError("captured key was null")
        assertTrue("Key should start with pref_dnd_duration_", key.startsWith("pref_dnd_duration_"))
        assertTrue("Key should end with fajr", key.endsWith("fajr"))
        // Verify value persisted correctly
        assertEquals(30, PrayerAlarmPrefs.getDNDDuration(PrayerType.FAJR))

        // No other preferences were modified
        assertEquals(10, PrayerAlarmPrefs.getDNDDuration(PrayerType.DHUHR))
        assertEquals(10, PrayerAlarmPrefs.getDNDDuration(PrayerType.ASR))

        testPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Test
    fun `toDNDDurationsMap returns all prayer types with correct values`() {
        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 15)
        PrayerAlarmPrefs.setDNDDuration(PrayerType.ISHA, 60)

        val map = PrayerAlarmPrefs.toDNDDurationsMap()
        assertEquals(6, map.size)

        // All types present
        for (type in PrayerType.entries) {
            assertTrue("Map should contain $type", map.containsKey(type))
        }

        // Specific values
        assertEquals(15, map[PrayerType.FAJR])
        assertEquals(60, map[PrayerType.ISHA])
        assertEquals(10, map[PrayerType.SUNRISE]) // default
        assertEquals(10, map[PrayerType.DHUHR])   // default
    }

    @Test
    fun `isDNDDurationKey matches correct key prefix`() {
        assertTrue(PrayerAlarmPrefs.isDNDDurationKey("pref_dnd_duration_fajr"))
        assertTrue(PrayerAlarmPrefs.isDNDDurationKey("pref_dnd_duration_isha"))
        assertFalse(PrayerAlarmPrefs.isDNDDurationKey("pref_dnd_enabled_fajr"))
        assertFalse(PrayerAlarmPrefs.isDNDDurationKey("pref_dnd_offset_fajr"))
        assertFalse(PrayerAlarmPrefs.isDNDDurationKey("pref_alarm_enabled_fajr"))
        assertFalse(PrayerAlarmPrefs.isDNDDurationKey(null))
    }

}

/**
 * A simple in-memory SharedPreferences implementation for testing.
 * Stores values in HashMaps and supports the basic read/edit/apply pattern.
 */
class InMemorySharedPreferences : SharedPreferences {

    private val storage = HashMap<String, Any?>()
    private val listeners = mutableSetOf<OnSharedPreferenceChangeListener>()
    private var editCount = 0

    override fun getAll(): MutableMap<String, *> = HashMap(storage)

    override fun getString(key: String, defValue: String?): String? {
        return storage[key] as? String ?: defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return (storage[key] as? MutableSet<String>) ?: defValues
    }

    override fun getInt(key: String, defValue: Int): Int {
        return (storage[key] as? Int) ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        return (storage[key] as? Long) ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return (storage[key] as? Float) ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return (storage[key] as? Boolean) ?: defValue
    }

    override fun contains(key: String): Boolean = storage.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private inner class InMemoryEditor : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removeKeys = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putStringSet(key: String, value: MutableSet<String>?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removeKeys.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            // Capture snapshot BEFORE clearing pending — listeners need changed keys
            val changedKeys = HashSet(pending.keys)
            val removedKeys = HashSet(removeKeys)

            if (clearAll) {
                storage.clear()
            }
            removedKeys.forEach { storage.remove(it) }
            changedKeys.forEach { key ->
                storage[key] = pending[key] // preserves null values (e.g. clear URI)
            }
            pending.clear()
            removeKeys.clear()
            clearAll = false
            editCount++

            // Notify listeners with captured keys
            for (key in changedKeys) {
                for (listener in listeners.toList()) {
                    listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, key)
                }
            }
        }
    }
}
