package com.mosquednd

import android.os.Bundle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.fragment.app.testing.launchFragmentInContainer
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.ui.settings.SettingsFragment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for SettingsFragment.
 * Verifies: preference screen loads with all preference categories,
 * calculation method change propagates and persists in SharedPreferences.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SettingsFragmentTest {

    @Test
    fun settingsScreen_isDisplayed() {
        // Launch the SettingsFragment in a container with the app theme
        val fragmentArgs = Bundle()
        launchFragmentInContainer<SettingsFragment>(
            fragmentArgs = fragmentArgs,
            themeResId = R.style.Theme_MosqueDND
        )

        // Verify the prayer calculation section is displayed
        onView(withText("Prayer Calculation"))
            .check(matches(isDisplayed()))

        // Verify the calculation method preference title
        onView(withText("Calculation Method"))
            .check(matches(isDisplayed()))

        // Verify the Asr method preference title
        onView(withText("Asr Method"))
            .check(matches(isDisplayed()))

        // Verify the notifications section is displayed
        onView(withText("Notifications"))
            .check(matches(isDisplayed()))

        // Verify the Adhan notifications switch exists
        onView(withText("Adhan Notifications"))
            .check(matches(isDisplayed()))

        // Verify the Geofence DND switch exists
        onView(withText("Geofence DND"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun changeCalculationMethod_persistsPreference() {
        // Launch the SettingsFragment
        val fragmentArgs = Bundle()
        launchFragmentInContainer<SettingsFragment>(
            fragmentArgs = fragmentArgs,
            themeResId = R.style.Theme_MosqueDND
        )

        // Click on the "Calculation Method" preference to open the dialog
        onView(withText("Calculation Method"))
            .perform(click())

        // The default value is "MWL (Muslim World League)".
        // Select "Islamic Society of North America" from the dialog.
        onView(withText("Islamic Society of North America"))
            .perform(click())

        // Verify the preference was persisted - SharedPreferences should now contain "isna"
        val prefs = MosqueDNDApp.prefs
        val savedMethod = prefs.getString("calculation_method", "mwl")
        assert(savedMethod == "isna") {
            "Expected calculation_method to be 'isna' but was '$savedMethod'"
        }
    }

    @Test
    fun changeAsrMethod_persistsPreference() {
        // Launch the SettingsFragment
        val fragmentArgs = Bundle()
        launchFragmentInContainer<SettingsFragment>(
            fragmentArgs = fragmentArgs,
            themeResId = R.style.Theme_MosqueDND
        )

        // Click on the "Asr Method" preference to open the dialog
        onView(withText("Asr Method"))
            .perform(click())

        // The default is "Standard (Shafi'i)". Select "Hanafi" from the dialog.
        onView(withText("Hanafi"))
            .perform(click())

        // Verify the preference was persisted
        val prefs = MosqueDNDApp.prefs
        val savedMethod = prefs.getString("asr_method", "standard")
        assert(savedMethod == "hanafi") {
            "Expected asr_method to be 'hanafi' but was '$savedMethod'"
        }
    }

    @Test
    fun changeHijriOffset_persistsPreference() {
        // Launch the SettingsFragment
        val fragmentArgs = Bundle()
        launchFragmentInContainer<SettingsFragment>(
            fragmentArgs = fragmentArgs,
            themeResId = R.style.Theme_MosqueDND
        )

        // Click on the "Hijri Date Offset" preference to open the dialog
        onView(withText("Hijri Date Offset"))
            .perform(click())

        // Select "+1" from the dialog
        onView(withText("+1"))
            .perform(click())

        // Verify the preference was persisted
        val prefs = MosqueDNDApp.prefs
        val savedOffset = prefs.getString("hijri_date_offset", "0")
        assert(savedOffset == "1") {
            "Expected hijri_date_offset to be '1' but was '$savedOffset'"
        }
        // Verify end-to-end propagation through PrayerAlarmPrefs
        assertEquals(1, PrayerAlarmPrefs.getHijriDateOffset())
    }
}
