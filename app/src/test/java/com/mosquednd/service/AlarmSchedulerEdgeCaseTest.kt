package com.mosquednd.service

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mosquednd.prayer.InMemorySharedPreferences
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerTime
import com.mosquednd.prayer.PrayerType
import com.mosquednd.util.PendingIntentConstants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 29, 30, 31, 33])
class AlarmSchedulerEdgeCaseTest {

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        val testPrefs = InMemorySharedPreferences()
        PrayerAlarmPrefs.initForTest(testPrefs)
    }

    @Test
    fun testC1_cancelAlarm_removes_scheduled_alarm_when_master_OFF() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        AlarmScheduler.schedulePrayerAlarm(
            context = appContext,
            prayerType = PrayerType.FAJR,
            prayerName = "Fajr",
            requestCode = PendingIntentConstants.PRAYER_ALARM_BASE + PrayerType.FAJR.ordinal,
            triggerMillisOverride = System.currentTimeMillis() + 60000
        )
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sam = Shadows.shadowOf(am)
        assertTrue("Alarm should be scheduled", sam.scheduledAlarms.isNotEmpty())
        AlarmScheduler.cancelAlarm(appContext, PendingIntentConstants.PRAYER_ALARM_BASE + PrayerType.FAJR.ordinal)
        assertTrue("No alarms after cancel", sam.scheduledAlarms.isEmpty())
    }

    @Test
    fun testC1_cancelAlarm_nonExistent_isIdempotent() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        val sam = Shadows.shadowOf(appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        AlarmScheduler.cancelAlarm(appContext, 999)
        assertTrue("No alarms", sam.scheduledAlarms.isEmpty())
    }

    @Test
    fun testC1_cancelAllPrayers_removes_all_scheduled() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        for (t in PrayerType.entries) {
            if (t == PrayerType.SUNRISE) continue
            AlarmScheduler.schedulePrayerAlarm(appContext, t, t.displayName, PendingIntentConstants.PRAYER_ALARM_BASE + t.ordinal, System.currentTimeMillis() + 60000)
        }
        val sam = Shadows.shadowOf(appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        assertTrue("Alarms scheduled", sam.scheduledAlarms.isNotEmpty())
        AlarmScheduler.cancelAllPrayers(appContext, PrayerType.entries.size)
        assertTrue("All cancelled", sam.scheduledAlarms.isEmpty())
    }

    @Test
    fun testH1_23_50_plus_10min_clamps() {
        val (ec, wc) = computeClampedEnd(23, 50, 0, 10)
        assertTrue("Should clamp", wc)
        assertEquals(23, ec.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, ec.get(Calendar.MINUTE))
        assertEquals(59, ec.get(Calendar.SECOND))
    }

    @Test
    fun testH1_23_50_plus_30min_clamps() {
        val (ec, wc) = computeClampedEnd(23, 50, 0, 30)
        assertTrue("Should clamp", wc)
        assertEquals(23, ec.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, ec.get(Calendar.MINUTE))
        assertEquals(59, ec.get(Calendar.SECOND))
    }

    @Test
    fun testH1_23_00_plus_60min_clamps() {
        val (ec, wc) = computeClampedEnd(23, 0, 0, 60)
        assertTrue("Should clamp", wc)
        assertEquals(23, ec.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, ec.get(Calendar.MINUTE))
        assertEquals(59, ec.get(Calendar.SECOND))
    }

    @Test
    fun testH1_23_59_plus_1min_clamps() {
        val (ec, wc) = computeClampedEnd(23, 59, 0, 1)
        assertTrue("Should clamp", wc)
        assertEquals(23, ec.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, ec.get(Calendar.MINUTE))
        assertEquals(59, ec.get(Calendar.SECOND))
    }

    @Test
    fun testH1_14_00_plus_30min_noClamp() {
        val (ec, wc) = computeClampedEnd(14, 0, 0, 30)
        assertFalse("No clamp", wc)
        assertEquals(14, ec.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, ec.get(Calendar.MINUTE))
    }

    @Test
    fun testH1_23_00_plus_30min_noClamp() {
        val (ec, wc) = computeClampedEnd(23, 0, 0, 30)
        assertFalse("No clamp", wc)
        assertEquals(23, ec.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, ec.get(Calendar.MINUTE))
    }

    @Test
    fun testH1_offset_pushes_start_then_clamps() {
        val (ec, wc) = computeClampedEnd(23, 40, 15, 10)
        assertTrue("Should clamp", wc)
        assertEquals(23, ec.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, ec.get(Calendar.MINUTE))
        assertEquals(59, ec.get(Calendar.SECOND))
    }

    @Test
    fun testH1_offset_pushes_start_sameDay_noClamp() {
        val (ec, wc) = computeClampedEnd(23, 45, 20, 30)
        assertFalse("No clamp", wc)
        assertEquals(0, ec.get(Calendar.HOUR_OF_DAY))
        assertEquals(35, ec.get(Calendar.MINUTE))
    }

    @Test
    fun testH2_freshStart_detectsChange() {
        val tdy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val (dc, up) = detectDayChange(-1, tdy)
        assertTrue("Fresh start detects change", dc); assertEquals(tdy, up)
    }

    @Test
    fun testH2_sameDay_noDetect() {
        val tdy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        assertFalse("Same day no change", detectDayChange(tdy, tdy).first)
    }

    @Test
    fun testH2_secondCheck_noRedetect() {
        val tdy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val (_, u) = detectDayChange(-1, tdy)
        assertFalse("No re-detect", detectDayChange(u, tdy).first)
    }

    @Test
    fun testH2_nextDay_detects() {
        val y = getYesterday()
        val tdy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val (dc, up) = detectDayChange(y, tdy)
        assertTrue("Next day detects", dc); assertEquals(tdy, up)
    }

    @Test
    fun testH2_dec31_to_jan1() {
        val (dc, up) = detectDayChange(365, 1)
        assertTrue("Dec31-Jan1 detects", dc); assertEquals(1, up)
    }

    @Test
    fun testH2_leapRollover() {
        val (dc, up) = detectDayChange(366, 1)
        assertTrue("Leap rollover detects", dc); assertEquals(1, up)
    }

    @Test
    fun testH2_threeDayTransition() {
        val (c1, u1) = detectDayChange(-1, 100)
        assertTrue(c1); assertEquals(100, u1)
        assertFalse(detectDayChange(u1, 100).first)
        val (c2, u2) = detectDayChange(u1, 101)
        assertTrue(c2); assertEquals(101, u2)
        assertFalse(detectDayChange(u2, 101).first)
        val (c3, u3) = detectDayChange(u2, 200)
        assertTrue(c3); assertEquals(200, u3)
    }

    @Test
    fun testH2_366_to_365() {
        assertTrue(detectDayChange(366, 365).first)
    }

    @Test
    fun testM1_emptyEnabledTypes_cancelsAll() {
        val bc = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
        val tm = mapOf(PrayerType.FAJR to PrayerTime(PrayerType.FAJR, bc, "12:00", false))
        AlarmScheduler.schedulePrayerAlarm(appContext, PrayerType.FAJR, "Fajr", PendingIntentConstants.PRAYER_ALARM_BASE + PrayerType.FAJR.ordinal, System.currentTimeMillis() + 60000)
        val sam = Shadows.shadowOf(appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        AlarmScheduler.rescheduleAll(appContext, tm, emptySet())
        assertTrue("Cancels all", sam.scheduledAlarms.isEmpty())
    }

    @Test
    fun testM1_noThrowWithValidTimesMap() {
        val bt = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
        val tm = mutableMapOf<PrayerType, PrayerTime>()
        for (t in PrayerType.entries) {
            if (t == PrayerType.SUNRISE) continue
            tm[t] = PrayerTime(t, bt.clone() as Calendar, "12:00", false)
        }
        try { AlarmScheduler.rescheduleAll(appContext, tm, setOf(PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR)) }
        catch (e: Exception) { fail("Should not throw"); return }
        assertTrue(Shadows.shadowOf(appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms.isNotEmpty())
    }

    @Test
    fun testM1_usesTimesMapNotCache() {
        val fc = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
        val tm = mapOf(PrayerType.ISHA to PrayerTime(PrayerType.ISHA, fc, "12:00", false))
        AlarmScheduler.rescheduleAll(appContext, tm, setOf(PrayerType.ISHA))
        assertTrue(Shadows.shadowOf(appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms.isNotEmpty())
    }

    private fun computeClampedEnd(sh: Int, sm: Int, off: Int, dur: Int): Pair<Calendar, Boolean> {
        val sc = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, sh); set(Calendar.MINUTE, sm); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MINUTE, off) }
        val ec = sc.clone() as Calendar; ec.add(Calendar.MINUTE, dur)
        val wc = ec.get(Calendar.DAY_OF_YEAR) != sc.get(Calendar.DAY_OF_YEAR)
        if (wc) { ec.set(Calendar.HOUR_OF_DAY, 23); ec.set(Calendar.MINUTE, 59); ec.set(Calendar.SECOND, 59); ec.set(Calendar.MILLISECOND, 0) }
        return Pair(ec, wc)
    }

    private fun detectDayChange(last: Int, sim: Int): Pair<Boolean, Int> {
        val dc = sim != last; return Pair(dc, if (dc) sim else last)
    }

    private fun getYesterday(): Int {
        val c = Calendar.getInstance(); val t = c.get(Calendar.DAY_OF_YEAR)
        if (t > 1) return t - 1; c.add(Calendar.YEAR, -1); return c.getActualMaximum(Calendar.DAY_OF_YEAR)
    }
}
