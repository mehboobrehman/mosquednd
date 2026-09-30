package com.mosquednd.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerType
import com.mosquednd.prayer.PrayerTime
import com.mosquednd.service.AlarmScheduler
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.InOrder
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
@LargeTest
class DNDAlarmE2ETest {

    @Before
    fun setUp() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(true)
        PrayerAlarmPrefs.setDNDEnabled(PrayerType.FAJR, true)
        PrayerAlarmPrefs.setDNDEnabled(PrayerType.DHUHR, true)
        PrayerAlarmPrefs.setDNDOffset(PrayerType.FAJR, 0)
        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 10)
        PrayerAlarmPrefs.setDNDOffset(PrayerType.DHUHR, 0)
        PrayerAlarmPrefs.setDNDDuration(PrayerType.DHUHR, 10)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After
    fun tearDown() {
        PrayerAlarmPrefs.setDNDEnabled(PrayerType.FAJR, false)
        PrayerAlarmPrefs.setDNDEnabled(PrayerType.DHUHR, false)
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun scheduleDNDAlarms_enabledPrayers_schedulesStartAndEnd() {
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

        AlarmScheduler.scheduleDNDAlarms(mockContext, timesMap, setOf(PrayerType.FAJR))

        // One start alarm + one end alarm = 2 total calls
        org.mockito.Mockito.verify(mockAlarmManager, org.mockito.Mockito.times(2)).setExactAndAllowWhileIdle(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun scheduleDNDAlarms_midnightCrossing_clampsEndTime() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockAlarmManager = org.mockito.Mockito.mock(AlarmManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.ALARM_SERVICE))
            .thenReturn(mockAlarmManager)

        val nearMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 50)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val clampedEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timesMap = mapOf(
            PrayerType.FAJR to PrayerTime(
                type = PrayerType.FAJR,
                time = Calendar.getInstance().apply { timeInMillis = nearMidnight },
                formattedTime = "05:00 AM"
            )
        )

        PrayerAlarmPrefs.setDNDDuration(PrayerType.FAJR, 30)

        AlarmScheduler.scheduleDNDAlarms(mockContext, timesMap, setOf(PrayerType.FAJR))

        // 2 calls expected: START at nearMidnight, END clamped to 23:59:59 of the same day.
        val inOrder: InOrder = org.mockito.Mockito.inOrder(mockAlarmManager)
        inOrder.verify(mockAlarmManager, org.mockito.Mockito.times(2)).setExactAndAllowWhileIdle(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any()
        )

        // Capture the actual triggerMillis values to verify the clamp.
        val triggerMillisCaptor = ArgumentCaptor.forClass(Long::class.java)
        org.mockito.Mockito.verify(mockAlarmManager, org.mockito.Mockito.times(2))
            .setExactAndAllowWhileIdle(
                org.mockito.ArgumentMatchers.anyInt(),
                triggerMillisCaptor.capture(),
                org.mockito.ArgumentMatchers.any()
            )

        // Explicitly assert we captured exactly 2 trigger times (START + END alarms)
        val capturedValues = triggerMillisCaptor.allValues
        org.junit.Assert.assertEquals(2, capturedValues.size)
        // Second call (END alarm) must be clamped to 23:59:59 of the start day.
        org.mockito.Mockito.verify(mockAlarmManager).setExactAndAllowWhileIdle(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.eq(clampedEnd),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun cancelDNDAlarms_callsAlarmManagerCancel() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockAlarmManager = org.mockito.Mockito.mock(AlarmManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.ALARM_SERVICE))
            .thenReturn(mockAlarmManager)

        AlarmScheduler.cancelDNDAlarms(mockContext)

        org.mockito.Mockito.verify(mockAlarmManager, org.mockito.Mockito.atLeastOnce()).cancel(
            org.mockito.ArgumentMatchers.any(PendingIntent::class.java)
        )
    }
}
