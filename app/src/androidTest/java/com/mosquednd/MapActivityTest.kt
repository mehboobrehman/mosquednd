package com.mosquednd

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.mosquednd.ui.MapActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso UI tests for MapActivity.
 * Verifies: MapView loads, save button exists, radius SeekBar exists,
 * and the activity launches with correct intent extras.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MapActivityTest {

    private lateinit var scenario: ActivityScenario<MapActivity>

    @Before
    fun setUp() {
        // Launch MapActivity with intent extras (mosque coordinates for pre-populated flow)
        val intent = Intent(
            Intent.ACTION_MAIN
        ).apply {
            putExtra("mosque_lat", 24.8967)
            putExtra("mosque_lng", 67.2369)
            putExtra("mosque_name", "Test Mosque")
            putExtra("mosque_radius", 200f)
        }
        scenario = ActivityScenario.launch<MapActivity>(intent)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun mapView_isDisplayed() {
        // The MapLibre MapView should be visible
        onView(withId(R.id.mapView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun toolbar_isDisplayed() {
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun saveButton_exists() {
        // The Save button should exist (may be visible after location selection)
        onView(withId(R.id.btn_save))
            .check(matches(withText("Save")))
    }

    @Test
    fun detectNearbyButton_exists() {
        onView(withId(R.id.btn_detect_nearby))
            .check(matches(withText("Detect Nearby")))
    }

    @Test
    fun radiusSeekBar_exists() {
        // The SeekBar for adjusting geofence radius should exist
        onView(withId(R.id.slider_radius))
            .check(matches(isDisplayed()))
    }

    @Test
    fun radiusTextDisplay_isShown() {
        // The radius value text view should show a default value
        onView(withId(R.id.text_radius_value))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mosqueNameInput_exists() {
        // The mosque name EditText should exist
        onView(withId(R.id.edit_mosque_name))
            .check(matches(isDisplayed()))
    }

    @Test
    fun myLocationFab_exists() {
        onView(withId(R.id.fab_my_location))
            .check(matches(isDisplayed()))
    }
}
