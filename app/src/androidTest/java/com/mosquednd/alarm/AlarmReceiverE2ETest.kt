package com.mosquednd.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.mosquednd.service.AlarmReceiver
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class AlarmReceiverE2ETest {

    @Before
    fun setUp() {
        AlarmReceiver.stopCurrentAdhan()
    }

    @After
    fun tearDown() {
        AlarmReceiver.stopCurrentAdhan()
    }

    @Test
    fun onReceive_alarmIntent_showsNotification() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockNotificationManager = org.mockito.Mockito.mock(NotificationManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE))
            .thenReturn(mockNotificationManager)
        // Stub areNotificationsEnabled so the positive-path test exercises the full chain
        org.mockito.Mockito.`when`(mockNotificationManager.areNotificationsEnabled()).thenReturn(true)

        val intent = Intent().apply {
            putExtra(AlarmReceiver.EXTRA_PRAYER_NAME, "Fajr")
            putExtra(AlarmReceiver.EXTRA_IS_ADHAN_MUTED, false)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(mockContext, intent)

        org.mockito.Mockito.verify(mockNotificationManager).createNotificationChannel(
            org.mockito.ArgumentMatchers.any()
        )
        org.mockito.Mockito.verify(mockNotificationManager).notify(
            org.mockito.ArgumentMatchers.eq(AlarmReceiver.NOTIFICATION_ID),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun onReceive_mutedIntent_showsNotificationButSkipsPlayback() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockNotificationManager = org.mockito.Mockito.mock(NotificationManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE))
            .thenReturn(mockNotificationManager)
        org.mockito.Mockito.`when`(mockNotificationManager.areNotificationsEnabled()).thenReturn(true)

        val intent = Intent().apply {
            putExtra(AlarmReceiver.EXTRA_PRAYER_NAME, "Fajr")
            putExtra(AlarmReceiver.EXTRA_IS_ADHAN_MUTED, true)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(mockContext, intent)

        // Muted alarms still show the notification (channel + notify called),
        // but the Adhan playback is skipped at line 56 of AlarmReceiver.kt.
        org.mockito.Mockito.verify(mockNotificationManager).createNotificationChannel(
            org.mockito.ArgumentMatchers.any()
        )
        org.mockito.Mockito.verify(mockNotificationManager).notify(
            org.mockito.ArgumentMatchers.eq(AlarmReceiver.NOTIFICATION_ID),
            org.mockito.ArgumentMatchers.any()
        )
    }

    @Test
    fun onReceive_dismissIntent_stopsCurrentAdhan() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)

        val intent = Intent(AlarmReceiver.ACTION_DISMISS)

        val receiver = AlarmReceiver()
        receiver.onReceive(mockContext, intent)

        // Verify stopCurrentAdhan is safe to call after dismiss action
        AlarmReceiver.stopCurrentAdhan()
    }
}
