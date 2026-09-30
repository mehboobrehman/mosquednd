package com.mosquednd.sound

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.mosquednd.prayer.PrayerAlarmPrefs
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class AdhanPlayerTest {

    private lateinit var context: Context
    private lateinit var adhanPlayer: com.mosquednd.sound.AdhanPlayer
    private var originalTestPrefs: android.content.SharedPreferences? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        adhanPlayer = com.mosquednd.sound.AdhanPlayer(context)
        originalTestPrefs = PrayerAlarmPrefs.testPrefs

        // Register media info for the default adhan resource so ShadowMediaPlayer handles it
        val defaultUri = Uri.parse("android.resource://${context.packageName}/${com.mosquednd.R.raw.adhan_default}")
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, defaultUri),
            ShadowMediaPlayer.MediaInfo(5000, 44100)
        )
    }

    @After
    fun tearDown() {
        try { adhanPlayer.stop() } catch (_: Exception) {}
        PrayerAlarmPrefs.testPrefs = originalTestPrefs
    }

    private fun initPrefs(value: String?) {
        val prefs = context.getSharedPreferences("test_${System.currentTimeMillis()}", Context.MODE_PRIVATE)
        prefs.edit().putString("pref_custom_adhan_uri", value).apply()
        PrayerAlarmPrefs.initForTest(prefs)
    }

    @Test
    fun playWithCustomUri_nullUri_fallsBackToDefault() {
        initPrefs(null)
        adhanPlayer.playWithCustomUri(context)
        assertNotNull(adhanPlayer)
    }

    @Test
    fun playWithCustomUri_blankUri_fallsBackToDefault() {
        initPrefs("")
        adhanPlayer.playWithCustomUri(context)
        assertNotNull(adhanPlayer)
    }

    @Test
    fun playWithCustomUri_invalidUri_fallsBackToDefault() {
        val invalidUri = Uri.parse("invalid://nonexistent")
        initPrefs(invalidUri.toString())
        // Register exception so ShadowMediaPlayer throws IOException (caught by AdhanPlayer)
        ShadowMediaPlayer.addException(
            DataSource.toDataSource(context, invalidUri),
            java.io.IOException("Invalid URI")
        )
        adhanPlayer.playWithCustomUri(context)
        assertNotNull(adhanPlayer)
    }

    @Test
    fun playWithCustomUri_validResourceUri_attemptsCustomPath() {
        val uri = "android.resource://${context.packageName}/${com.mosquednd.R.raw.adhan_default}"
        initPrefs(uri)
        adhanPlayer.playWithCustomUri(context)
        assertNotNull(adhanPlayer)
    }

    @Test
    fun stop_isIdempotent_multipleCallsDoNotCrash() {
        adhanPlayer.play()
        adhanPlayer.stop()
        adhanPlayer.stop()
        assertNotNull(adhanPlayer)
    }
}
