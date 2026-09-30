package com.mosquednd.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.net.Uri
import android.util.Log
import com.mosquednd.R
import com.mosquednd.prayer.PrayerAlarmPrefs
import java.io.IOException

/**
 * Manages Adhan (Islamic call to prayer) audio playback.
 * Wraps Android's MediaPlayer with proper lifecycle management and error handling.
 *
 * Default Adhan audio file location:
 *   Place your MP3 file at: app/src/main/res/raw/adhan_default.mp3
 *   (A minimal placeholder exists at this path for build compatibility.)
 */
class AdhanPlayer(private val context: Context) {

    private val TAG = "AdhanPlayer"

    var onCompletion: (() -> Unit)? = null
    var onFocusLost: (() -> Unit)? = null

    private var mediaPlayer: MediaPlayer? = null

    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null

    private fun requestAudioFocus(): Boolean {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            Log.d(TAG, "Audio focus lost ($focusChange) — stopping playback")
                            stop()
                            onFocusLost?.invoke()
                        }
                    }
                }
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            Log.d(TAG, "Audio focus lost legacy ($focusChange) — stopping playback")
                            stop()
                            onFocusLost?.invoke()
                        }
                    }
                },
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    /**
     * Plays the default Adhan from raw resources (R.raw.adhan_default).
     * Uses prepareAsync() for non-blocking preparation.
     * Logs errors gracefully if the audio resource is missing.
     */
    fun play() {
        stop()

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )

                setDataSource(context, Uri.parse("android.resource://${context.packageName}/${R.raw.adhan_default}"))

                setOnPreparedListener { mp ->
                    if (requestAudioFocus()) {
                        mp.start()
                        Log.d(TAG, "Default Adhan started with focus")
                    } else {
                        Log.w(TAG, "Could not acquire audio focus, skipping playback")
                        mp.stop()
                    }
                }

                setOnCompletionListener { mp ->
                    Log.d(TAG, "Adhan playback completed")
                    abandonAudioFocus()
                    mp.release()
                    mediaPlayer = null
                    onCompletion?.invoke()
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    mp.release()
                    mediaPlayer = null
                    true
                }

                prepareAsync()
            }

            mediaPlayer = player
            Log.d(TAG, "Preparing default Adhan audio")
        } catch (e: IOException) {
            Log.e(TAG, "IOException preparing default Adhan: ${e.message}", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException preparing default Adhan: ${e.message}", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException preparing default Adhan: ${e.message}", e)
        }
    }

    /**
     * Plays a custom Adhan from a user-selected URI (content:// or file://).
     * Reads the URI from PrayerAlarmPrefs. Falls back to the default Adhan
     * if no custom URI is set or if playback of the custom URI fails.
     *
     * @param context Context for accessing content URIs and SharedPreferences
     */
    fun playWithCustomUri(context: Context) {
        val uriString = PrayerAlarmPrefs.getCustomAdhanUri()
        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                stop()

                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )

                    setDataSource(context, uri)

                    setOnPreparedListener { mp ->
                        if (requestAudioFocus()) {
                            mp.start()
                            Log.d(TAG, "Custom Adhan started from URI: $uri with focus")
                        } else {
                            Log.w(TAG, "Could not acquire audio focus, skipping playback")
                            mp.stop()
                        }
                    }

                    setOnCompletionListener { mp ->
                        Log.d(TAG, "Custom Adhan playback completed")
                        abandonAudioFocus()
                        mp.release()
                        mediaPlayer = null
                        onCompletion?.invoke()
                    }

                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "MediaPlayer error (URI): what=$what, extra=$extra")
                        mp.release()
                        mediaPlayer = null
                        true
                    }

                    prepareAsync()
                }

                mediaPlayer = player
                Log.d(TAG, "Preparing custom Adhan audio from URI: $uri")
                return
            } catch (e: IOException) {
                Log.w(TAG, "IOException playing custom URI, falling back to default: ${e.message}", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException playing custom URI, falling back to default: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "IllegalStateException playing custom URI, falling back to default: ${e.message}", e)
            }
            // Fall through to default playback
        }

        // No custom URI or error — play default
        play()
    }

    /**
     * Stops playback and releases MediaPlayer resources.
     * Safe to call multiple times — null-safe on mediaPlayer.
     */
    fun stop() {
        abandonAudioFocus()
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                    Log.d(TAG, "Adhan playback stopped")
                }
                player.reset()
                player.release()
                Log.d(TAG, "MediaPlayer resources released")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "IllegalStateException during stop: ${e.message}", e)
            } finally {
                mediaPlayer = null
            }
        }
    }

    /**
     * Returns whether audio is currently playing.
     * Safe to call when mediaPlayer is null.
     */
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException checking isPlaying: ${e.message}", e)
            false
        }
    }
}
