package com.mosquednd.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mosquednd.R
import com.mosquednd.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PrayerTimesWidgetNavigationTest {

    @Test
    fun `widget launch intent with prayer_times extra opens MainActivity with prayer times selected`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "prayer_times")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val scenario = ActivityScenario.launch<MainActivity>(intent)

        scenario.onActivity { activity ->
            val bottomNav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            assert(bottomNav.selectedItemId == R.id.nav_prayer_times) {
                "Expected nav_prayer_times selected, but was ${bottomNav.selectedItemId}"
            }
        }

        scenario.close()
    }

    @Test
    fun `existing activity navigates to prayer times on widget relaunch intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))

        scenario.onActivity { activity ->
            val newIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, "prayer_times")
            }
            activity.onNewIntent(newIntent)

            val bottomNav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            assert(bottomNav.selectedItemId == R.id.nav_prayer_times) {
                "Expected nav_prayer_times selected on new intent, but was ${bottomNav.selectedItemId}"
            }
        }

        scenario.close()
    }
}
