package com.mosquednd.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerType
import com.mosquednd.prayer.PrayerTime
import com.mosquednd.service.AlarmScheduler
import com.mosquednd.util.PendingIntentConstants
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
@LargeTest
class PrayerAlarmE2ETest {

    @Before
    fun setUp() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(true)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.FAJR, true)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After
    fun tearDown() {
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.FAJR, false)
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun schedulePrayerAlarm_futureTime_callsAlarmManager() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockAlarmManager = org.mockito.Mockito.mock(AlarmManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.ALARM_SERVICE))
            .thenReturn(mockAlarmManager)

        val futureTime = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis

        AlarmScheduler.schedulePrayerAlarm(
            context = mockContext,
            prayerType = PrayerType.FAJR,
            prayerName = "Fajr",
            requestCode = PendingIntentConstants.PRAYER_ALARM_BASE + PrayerType.FAJR.ordinal,
            triggerMillisOverride = futureTime
        )

        org.mockito.Mockito.verify(mockAlarmManager).setExactAndAllowWhileIdle(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun schedulePrayerAlarm_pastTime_neverCallsAlarmManager() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockAlarmManager = org.mockito.Mockito.mock(AlarmManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.ALARM_SERVICE))
            .thenReturn(mockAlarmManager)

        val pastTime = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, -1)
        }.timeInMillis

        AlarmScheduler.schedulePrayerAlarm(
            context = mockContext,
            prayerType = PrayerType.FAJR,
            prayerName = "Fajr",
            requestCode = PendingIntentConstants.PRAYER_ALARM_BASE + PrayerType.FAJR.ordinal,
            triggerMillisOverride = pastTime
        )

        org.mockito.Mockito.verify(mockAlarmManager, org.mockito.Mockito.never()).setExactAndAllowWhileIdle(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any()
        )
        org.mockito.Mockito.verify(mockAlarmManager, org.mockito.Mockito.never()).setExact(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun cancelAlarm_callsAlarmManagerCancel() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockAlarmManager = org.mockito.Mockito.mock(AlarmManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.ALARM_SERVICE))
            .thenReturn(mockAlarmManager)

        AlarmScheduler.cancelAlarm(mockContext, PendingIntentConstants.PRAYER_ALARM_BASE + PrayerType.FAJR.ordinal)

        org.mockito.Mockito.verify(mockAlarmManager).cancel(org.mockito.ArgumentMatchers.any(PendingIntent::class.java))
    }

    @Test
    fun scheduleAllPrayers_sizeMismatch_skipsAllScheduling() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockAlarmManager = org.mockito.Mockito.mock(AlarmManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.ALARM_SERVICE))
            .thenReturn(mockAlarmManager)

        val times = listOf<Calendar>()
        val names = listOf("Fajr")

        AlarmScheduler.scheduleAllPrayers(mockContext, times, names)

        org.mockito.Mockito.verify(mockAlarmManager, org.mockito.Mockito.never()).setExactAndAllowWhileIdle(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun rescheduleAll_emptyEnabledTypes_skipsAllScheduling() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockAlarmManager = org.mockito.Mockito.mock(AlarmManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.ALARM_SERVICE))
            .thenReturn(mockAlarmManager)

        val futureTime = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis

        val timesMap = mapOf(
            PrayerType.FAJR to PrayerTime(
                type = PrayerType.FAJR,
                time = Calendar.getInstance().apply { timeInMillis = futureTime },
                formattedTime = "05:00 AM"
            )
        )

        // Empty enabled set means no prayer types get rescheduled.
        AlarmScheduler.rescheduleAll(mockContext, timesMap, emptySet())

        org.mockito.Mockito.verify(mockAlarmManager, org.mockito.Mockito.never()).setExactAndAllowWhileIdle(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun rescheduleAll_enabledTypes_cancelsAndReschedules() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockAlarmManager = org.mockito.Mockito.mock(AlarmManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.ALARM_SERVICE))
            .thenReturn(mockAlarmManager)

        val futureTime = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis

        val timesMap = mapOf(
            PrayerType.FAJR to PrayerTime(
                type = PrayerType.FAJR,
                time = Calendar.getInstance().apply { timeInMillis = futureTime },
                formattedTime = "05:00 AM"
            )
        )

        AlarmScheduler.rescheduleAll(mockContext, timesMap, setOf(PrayerType.FAJR))

        org.mockito.Mockito.verify(mockAlarmManager).cancel(org.mockito.ArgumentMatchers.any(PendingIntent::class.java))
        org.mockito.Mockito.verify(mockAlarmManager).setExactAndAllowWhileIdle(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any()
        )
    }
}
