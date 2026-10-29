package com.mosquednd.whatsnew

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mosquednd.BuildConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WhatsNewManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear preferences before each test
        context.getSharedPreferences("whats_new_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testShouldShowOnFirstInstall() {
        // By default last_seen_version_code is 0
        // BuildConfig.VERSION_CODE should be > 0 (it's 12 in our case)
        assertTrue("Should show on first install/launch if not seen yet", WhatsNewManager.shouldShow(context))
    }

    @Test
    fun testMarkVersionSeen() {
        WhatsNewManager.markVersionSeen(context)
        assertFalse("Should not show after marking as seen", WhatsNewManager.shouldShow(context))
    }

    @Test
    fun testShouldShowOnUpdate() {
        // Simulate an older version was seen
        context.getSharedPreferences("whats_new_prefs", Context.MODE_PRIVATE).edit()
            .putInt("last_seen_version_code", 10)
            .commit()
        
        // current version is 12
        assertTrue("Should show when current version is newer than last seen", WhatsNewManager.shouldShow(context))
    }

    @Test
    fun testNoShowWhenSameVersion() {
        // Simulate current version was already seen
        val currentVersion = WhatsNewManager.getCurrentVersionCode()
        context.getSharedPreferences("whats_new_prefs", Context.MODE_PRIVATE).edit()
            .putInt("last_seen_version_code", currentVersion)
            .commit()
        
        assertFalse("Should not show when current version is same as last seen", WhatsNewManager.shouldShow(context))
    }

    @Test
    fun testReleaseNoteRepository() {
        val notes = ReleaseNoteRepository.getReleaseNotes()
        assertNotNull(notes)
        assertTrue(notes.isNotEmpty())
        
        val latest = ReleaseNoteRepository.getLatestReleaseNote()
        assertNotNull(latest)
        assertEquals("v1.0.7", latest?.versionName)
        
        val v102 = ReleaseNoteRepository.getReleaseNoteForVersion("v1.0.2")
        assertNotNull(v102)
        assertEquals(11, v102?.versionCode)
        
        val categories = v102?.items?.map { it.category }?.distinct()
        assertTrue(categories?.contains(ReleaseNoteCategory.WHATS_NEW) == true)
        assertTrue(categories?.contains(ReleaseNoteCategory.BUG_FIXES) == true)
    }
}
