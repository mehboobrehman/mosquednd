package com.mosquednd.ui.prayer

import android.app.AlarmManager
import android.content.Context
import com.mosquednd.prayer.InMemorySharedPreferences
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerType
import com.mosquednd.service.AlarmScheduler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 29, 30, 31, 33])
class PrayerTimesFragmentToggleTest {

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        val testPrefs = InMemorySharedPreferences()
        PrayerAlarmPrefs.initForTest(testPrefs)
    }

    @Test
    fun `cancelAlarm removes scheduled prayer alarm when master is OFF`() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        val requestCode = PrayerType.FAJR.ordinal

        AlarmScheduler.schedulePrayerAlarm(
            context = appContext,
            prayerType = PrayerType.FAJR,
            prayerName = "Fajr",
            requestCode = requestCode,
            triggerMillisOverride = System.currentTimeMillis() + 60000
        )

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        val scheduledBefore = shadowAlarmManager.scheduledAlarms
        assertTrue("Alarm should be scheduled before cancel", scheduledBefore.size >= 1)

        AlarmScheduler.cancelAlarm(appContext, requestCode)

        val scheduledAfter = shadowAlarmManager.scheduledAlarms
        assertTrue("Alarm should be cancelled after cancelAlarm", scheduledAfter.isEmpty())
    }

    @Test
    fun `cancelAlarm is idempotent when no alarm exists and master is OFF`() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        val requestCode = PrayerType.DHUHR.ordinal

        AlarmScheduler.cancelAlarm(appContext, requestCode)

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        assertTrue("No alarms should be scheduled", shadowAlarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun `master OFF toggle preference update does not schedule new alarm`() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.ASR, true)

        assertTrue("ASR preference should be enabled", PrayerAlarmPrefs.isAlarmEnabled(PrayerType.ASR))
        assertFalse("Master alarm should remain disabled", PrayerAlarmPrefs.isMasterAlarmEnabled())

        AlarmScheduler.cancelAlarm(appContext, PrayerType.ASR.ordinal)

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        assertTrue("No alarm should be scheduled when master is OFF", shadowAlarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun `master ON toggle preference update works correctly`() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(true)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.MAGHRIB, true)

        assertTrue("MAGHRIB preference should be enabled", PrayerAlarmPrefs.isAlarmEnabled(PrayerType.MAGHRIB))
        assertTrue("Master alarm should be enabled", PrayerAlarmPrefs.isMasterAlarmEnabled())
    }

    @Test
    fun `master ON toggle disabled updates preference correctly`() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(true)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.ISHA, true)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.ISHA, false)

        assertFalse("ISHA preference should be disabled", PrayerAlarmPrefs.isAlarmEnabled(PrayerType.ISHA))
        assertTrue("Master alarm should remain enabled", PrayerAlarmPrefs.isMasterAlarmEnabled())
    }
}
