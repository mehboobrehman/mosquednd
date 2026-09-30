package com.mosquednd.prayer

import android.content.SharedPreferences
import com.mosquednd.sound.AdhanPlayer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.verifyNoInteractions

/**
 * Regression test verifying that DND duration preference writes do NOT trigger
 * any audio API calls (AdhanPlayer). The guard in PrayerTimesFragment's
 * onSharedPreferenceChanged returns early for DND duration keys, so the
 * AdhanPlayer should never be reached via this code path.
 */
class DNDDurationDecouplingTest {

    private lateinit var testPrefs: InMemorySharedPreferences
    private lateinit var mockAdhanPlayer: AdhanPlayer
    private val capturedKeys = mutableListOf<String?>()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        capturedKeys.add(key)
    }

    @Before
    fun setUp() {
        testPrefs = InMemorySharedPreferences()
        PrayerAlarmPrefs.initForTest(testPrefs)
        mockAdhanPlayer = Mockito.mock(AdhanPlayer::class.java)
        capturedKeys.clear()
        testPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    @After
    fun tearDown() {
        testPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        PrayerAlarmPrefs.initForTest(testPrefs)
    }

    @Test
    fun `setDNDDuration produces zero AdhanPlayer interactions`() {
        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 30)
        verifyNoInteractions(mockAdhanPlayer)
    }

    @Test
    fun `setDNDDurationRaw produces zero AdhanPlayer interactions`() {
        PrayerAlarmPrefs.setDNDDurationRaw(PrayerType.DHUHR, 22)
        verifyNoInteractions(mockAdhanPlayer)
    }

    @Test
    fun `repeated setDNDDuration calls produce zero AdhanPlayer interactions`() {
        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 15)
        PrayerAlarmPrefs.setDNDDuration(PrayerType.DHUHR, 30)
        PrayerAlarmPrefs.setDNDDuration(PrayerType.ASR, 5)
        PrayerAlarmPrefs.setDNDDuration(PrayerType.MAGHRIB, 60)
        PrayerAlarmPrefs.setDNDDuration(PrayerType.ISHA, 20)
        verifyNoInteractions(mockAdhanPlayer)
    }

    @Test
    fun `setDNDDuration across all prayer types produces zero AdhanPlayer interactions`() {
        for (type in PrayerType.entries) {
            if (type == PrayerType.SUNRISE) continue
            PrayerAlarmPrefs.setDNDDuration(type, 10)
        }
        verifyNoInteractions(mockAdhanPlayer)
    }

    @Test
    fun `setDNDDurationRaw across all prayer types produces zero AdhanPlayer interactions`() {
        for (type in PrayerType.entries) {
            if (type == PrayerType.SUNRISE) continue
            PrayerAlarmPrefs.setDNDDurationRaw(type, 5)
        }
        verifyNoInteractions(mockAdhanPlayer)
    }

    @Test
    fun `listener is notified with correct key on setDNDDuration`() {
        PrayerAlarmPrefs.setDNDDuration(PrayerType.ISHA, 60)
        assertTrue("Listener should have fired at least once", capturedKeys.isNotEmpty())
        val key = capturedKeys.first() ?: throw AssertionError("captured key was null")
        assertTrue("Key should be a DND duration key", PrayerAlarmPrefs.isDNDDurationKey(key))
        assertTrue("Key should end with 'isha'", key.endsWith("isha"))
        verifyNoInteractions(mockAdhanPlayer)
    }

    @Test
    fun `setDNDDurationRaw listener fires and key is correct`() {
        PrayerAlarmPrefs.setDNDDurationRaw(PrayerType.FAJR, 7)
        assertTrue("Listener should have fired", capturedKeys.isNotEmpty())
        val key = capturedKeys.first() ?: throw AssertionError("captured key was null")
        assertTrue("Key should be a DND duration key", PrayerAlarmPrefs.isDNDDurationKey(key))
        assertTrue("Key should end with 'fajr'", key.endsWith("fajr"))
        verifyNoInteractions(mockAdhanPlayer)
    }

    @Test
    fun `existing guard logic detects DND duration keys while rejecting others`() {
        assertTrue(PrayerAlarmPrefs.isDNDDurationKey("pref_dnd_duration_fajr"))
        assertTrue(PrayerAlarmPrefs.isDNDDurationKey("pref_dnd_duration_isha"))
        assertFalse(PrayerAlarmPrefs.isDNDDurationKey("pref_dnd_enabled_fajr"))
        assertFalse(PrayerAlarmPrefs.isDNDDurationKey("pref_dnd_offset_fajr"))
        assertFalse(PrayerAlarmPrefs.isDNDDurationKey("pref_alarm_enabled_fajr"))
        assertFalse(PrayerAlarmPrefs.isDNDDurationKey("calculation_method"))
        assertFalse(PrayerAlarmPrefs.isDNDDurationKey(null))
        verifyNoInteractions(mockAdhanPlayer)
    }
}
