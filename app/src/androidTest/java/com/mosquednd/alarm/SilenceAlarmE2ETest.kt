package com.mosquednd.alarm

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.mosquednd.receiver.SilenceAlarmReceiver
import com.mosquednd.service.AlarmReceiver
import com.mosquednd.ui.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor

@RunWith(AndroidJUnit4::class)
@LargeTest
class SilenceAlarmE2ETest {

    @Before
    fun setUp() {
        AlarmReceiver.stopCurrentAdhan()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After
    fun tearDown() {
        AlarmReceiver.stopCurrentAdhan()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun onReceive_silenceIntent_cancelsNotificationAndOpensApp() {
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        val mockNotificationManager = org.mockito.Mockito.mock(NotificationManager::class.java)
        org.mockito.Mockito.`when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE))
            .thenReturn(mockNotificationManager)

        val intent = Intent().apply {
            putExtra(SilenceAlarmReceiver.EXTRA_PRAYER_NAME, "Fajr")
        }

        val receiver = SilenceAlarmReceiver()
        receiver.onReceive(mockContext, intent)

        org.mockito.Mockito.verify(mockNotificationManager).cancel(AlarmReceiver.NOTIFICATION_ID)

        // Verify MainActivity is launched with the correct flags (NEW_TASK required for
        // BroadcastReceiver context, CLEAR_TOP + SINGLE_TOP for proper back-stack behaviour).
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        org.mockito.Mockito.verify(mockContext).startActivity(intentCaptor.capture())

        val expectedComponent = ComponentName("com.mosquednd", MainActivity::class.java.name)
        org.mockito.Mockito.verify(mockContext).startActivity(
            org.mockito.ArgumentMatchers.argThat { launchIntent: Intent ->
                launchIntent.flags == (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        && launchIntent.component == expectedComponent
            }
        )
    }
}
