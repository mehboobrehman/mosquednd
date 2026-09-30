package com.mosquednd.prayer

import android.content.Context
import android.util.Log
import com.mosquednd.util.DNDController

/**
 * Singleton that tracks per-prayer DND windows with reference-counting.
 *
 * When the first per-prayer DND window starts (and no auto/geofence DND is active),
 * it calls DNDController.enableDND. When the last window ends, it calls
 * DNDController.disableDND when no auto/geofence DND is active.
 *
 * Auto/geofence DND takes priority: if DNDController.isTimerActive returns true,
 * per-prayer DND does not change DND state.
 */
object PrayerDNDManager {
    private const val TAG = "PrayerDNDManager"
    /** Tracks which prayers currently have an active DND window. */
    private val activeDNDPrayers = mutableSetOf<PrayerType>()

    /**
     * Called when a per-prayer DND window starts.
     * Adds type to the active set. If first and no auto DND active, enables DND.
     */
    fun onDNDStart(type: PrayerType, context: Context) {
        activeDNDPrayers.add(type)
        logDebug("onDNDStart " + type + " - activeSize=" + activeDNDPrayers.size)
        if (activeDNDPrayers.size == 1) {
            if (!DNDController.isTimerActive()) {
                val duration = PrayerAlarmPrefs.getDNDDuration(type)
                DNDController.enableDND(context, duration)
                logDebug("DND enabled for " + type + " duration=" + duration + "min")
            } else {
                logDebug("auto/geofence DND active - skip enableDND " + type)
            }
        } else {
            logDebug("other prayer already active - skip enableDND " + type)
        }
    }

    /** Called when a per-prayer DND window ends. */
    fun onDNDEnd(type: PrayerType, context: Context) {
        activeDNDPrayers.remove(type)
        logDebug("onDNDEnd " + type + " - activeSize=" + activeDNDPrayers.size)
        if (activeDNDPrayers.isEmpty()) {
            if (!DNDController.isTimerActive()) {
                DNDController.disableDND(context)
                logDebug("DND disabled - no more active prayers")
            } else {
                logDebug("auto/geofence DND active - skip disableDND")
            }
        } else {
            logDebug("other prayers still active - skip disableDND")
        }
    }

    /** Cancels all per-prayer DND windows immediately. */
    fun cancelDND(context: Context) {
        activeDNDPrayers.clear()
        logDebug("cancelDND - active set cleared")
        if (!DNDController.isTimerActive()) {
            DNDController.disableDND(context)
            logDebug("DND disabled via cancelDND")
        } else {
            logDebug("auto/geofence DND still active - keeping DND")
        }
    }

    /** Returns a defensive copy of active DND prayers. */
    fun getActiveDNDPrayers(): Set<PrayerType> = activeDNDPrayers.toSet()
    /** Returns true if any per-prayer DND window is active. */
    fun isAnyPrayerDNDActive(): Boolean = activeDNDPrayers.isNotEmpty()

    private fun logDebug(msg: String) {
        try { Log.d(TAG, msg) } catch (_: RuntimeException) { }
    }
}
