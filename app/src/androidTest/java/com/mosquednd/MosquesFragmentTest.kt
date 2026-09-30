package com.mosquednd

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.fragment.app.testing.launchFragmentInContainer
import com.mosquednd.ui.MosquesFragment
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MosquesFragmentTest {

    @Test
    fun emptyState_recyclerView_isDisplayed() {
        launchFragmentInContainer<MosquesFragment>(
            themeResId = R.style.Theme_MosqueDND
        )
        onView(withId(R.id.recycler_mosques))
            .check(matches(isDisplayed()))
    }

    @Test
    fun emptyState_title_isDisplayed() {
        launchFragmentInContainer<MosquesFragment>(
            themeResId = R.style.Theme_MosqueDND
        )
        onView(withId(R.id.text_mosques_title))
            .check(matches(isDisplayed()))
    }

    @Test
    fun emptyState_noMosquesText_isDisplayed() {
        launchFragmentInContainer<MosquesFragment>(
            themeResId = R.style.Theme_MosqueDND
        )
        onView(withId(R.id.empty_state))
            .check(matches(isDisplayed()))
    }

    @Test
    fun emptyState_addMosqueButton_isDisplayed() {
        launchFragmentInContainer<MosquesFragment>(
            themeResId = R.style.Theme_MosqueDND
        )
        onView(withId(R.id.btn_add_mosque_empty))
            .check(matches(isDisplayed()))
    }
}
