package com.mosquednd

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.mosquednd.receiver.BootReceiver
import com.mosquednd.receiver.DailyRecalculationHelper
import com.mosquednd.service.GeofenceService
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito

/**
 * End-to-end boot-flow test verifying that BOOT_COMPLETED triggers exactly one
 * recalculation and one geofence re-registration, with no duplicate work.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BootFlowE2ETest {

    private lateinit var dailyRecalcMock: MockedStatic<DailyRecalculationHelper>
    private lateinit var geofenceMock: MockedStatic<GeofenceService>
    private lateinit var logMock: MockedStatic<Log>
    private lateinit var bootReceiver: BootReceiver
    private lateinit var context: Context

    @Before
    fun setUp() {
        bootReceiver = BootReceiver()
        context = ApplicationProvider.getApplicationContext()
        logMock = Mockito.mockStatic(Log::class.java)
        dailyRecalcMock = Mockito.mockStatic(DailyRecalculationHelper::class.java)
        geofenceMock = Mockito.mockStatic(GeofenceService::class.java)
    }

    @After
    fun tearDown() {
        dailyRecalcMock.close()
        geofenceMock.close()
        logMock.close()
    }

    @Test
    fun `boot broadcast triggers exactly one recalc and one geofence re-register`() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // Try sending broadcast. Some platforms block sending BOOT_COMPLETED
        // from third-party apps even with RECEIVE_BOOT_COMPLETED; fall back
        // to direct invocation which is functionally equivalent.
        try {
            context.sendBroadcast(intent)
        } catch (e: SecurityException) {
            bootReceiver.onReceive(context, intent)
        }

        dailyRecalcMock.verify({ DailyRecalculationHelper.recalculateAndReschedule(context) }, org.mockito.Mockito.times(1))
        geofenceMock.verify({ GeofenceService.enqueueReRegisterAll(context) }, org.mockito.Mockito.times(1))
    }
}
 
