package com.mosquednd.alarm

import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.fragment.app.testing.launchFragmentInContainer
import com.mosquednd.R
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerType
import com.mosquednd.ui.prayer.PrayerTimesFragment
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PrayerTimesFragmentE2ETest {

    private var initialMasterAlarmState: Boolean = true

    @Before
    fun setUp() {
        // Capture initial state so tearDown can restore it — guards against intermediate failures.
        initialMasterAlarmState = PrayerAlarmPrefs.isMasterAlarmEnabled()
        PrayerAlarmPrefs.setMasterAlarmEnabled(true)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.FAJR, true)
    }

    @After
    fun tearDown() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(initialMasterAlarmState)
    }

    private fun switchAtPosition(position: Int): Matcher<View> {
        return object : org.hamcrest.TypeSafeMatcher<View>() {
            override fun describeTo(description: org.hamcrest.Description) {
                description.appendText("RecyclerView child switch at position $position")
            }

            override fun matchesSafely(item: View): Boolean {
                val parent = item.parent ?: return false
                val recyclerView = parent.parent as? RecyclerView ?: return false
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(position) ?: return false
                val switch = viewHolder.itemView.findViewById<SwitchCompat>(R.id.switch_alarm_toggle)
                return switch != null && switch === item
            }
        }
    }

    private fun waitForIdleSync() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun masterAlarmOff_disablesPerPrayerToggles() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        waitForIdleSync()

        launchFragmentInContainer<PrayerTimesFragment>(
            themeResId = R.style.Theme_MosqueDND
        )

        onView(withId(R.id.recycler_prayer_times)).check(matches(isDisplayed()))
        onView(switchAtPosition(0)).check(matches(org.hamcrest.Matchers.not(isEnabled())))
    }

    @Test
    fun masterAlarmOn_enablesPerPrayerToggles() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(true)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.FAJR, true)
        waitForIdleSync()

        launchFragmentInContainer<PrayerTimesFragment>(
            themeResId = R.style.Theme_MosqueDND
        )

        onView(withId(R.id.recycler_prayer_times)).check(matches(isDisplayed()))
        onView(switchAtPosition(0)).check(matches(isEnabled()))
    }

    @Test
    fun masterAlarmOff_preservesDisabledStateAfterToggle() {
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.FAJR, false)
        waitForIdleSync()

        launchFragmentInContainer<PrayerTimesFragment>(
            themeResId = R.style.Theme_MosqueDND
        )

        onView(withId(R.id.recycler_prayer_times)).check(matches(isDisplayed()))
        onView(switchAtPosition(0)).check(matches(org.hamcrest.Matchers.not(isEnabled())))
    }

    @Test
    fun masterAlarmTransition_onToOff_disablesPerPrayerToggles() {
        // Start with master ON (setUp does this), then flip to OFF.
        PrayerAlarmPrefs.setMasterAlarmEnabled(true)
        PrayerAlarmPrefs.setAlarmEnabled(PrayerType.FAJR, true)
        waitForIdleSync()

        launchFragmentInContainer<PrayerTimesFragment>(
            themeResId = R.style.Theme_MosqueDND
        )

        // Verify enabled state before transition.
        onView(withId(R.id.recycler_prayer_times)).check(matches(isDisplayed()))
        onView(switchAtPosition(0)).check(matches(isEnabled()))

        // Simulate the C1 regression: master toggled OFF after fragment is already shown.
        PrayerAlarmPrefs.setMasterAlarmEnabled(false)
        waitForIdleSync()

        // The per-prayer toggle must become disabled as soon as master is OFF.
        onView(switchAtPosition(0)).check(matches(org.hamcrest.Matchers.not(isEnabled())))
    }
}
