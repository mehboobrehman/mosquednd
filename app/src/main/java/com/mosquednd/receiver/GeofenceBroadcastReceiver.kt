package com.mosquednd.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.mosquednd.data.MosqueRepository
import com.mosquednd.geofence.GeofenceFilter
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.util.DNDController
import com.mosquednd.util.LogHelper

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.w(TAG, "Received null geofencing event")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorCode = geofencingEvent.errorCode
            Log.e(TAG, "Geofencing event error: $errorCode")
            LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "GEOFENCE_EVENT_ERROR", mapOf("errorCode" to errorCode))
            return
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences
        if (triggeringGeofences.isNullOrEmpty()) {
            Log.w(TAG, "Geofence transition with no triggering geofences")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "GEOFENCE_EVENT", mapOf("transition" to geofenceTransition, "count" to triggeringGeofences.size))
        val filter = GeofenceFilter.getInstance()

        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "ENTER transition for ${triggeringGeofences.size} geofence(s)")

                val existingMosqueIds = MosqueRepository.getAllMosques().map { it.id }.toSet()
                val validGeofences = triggeringGeofences.filter { it.requestId in existingMosqueIds }

                if (validGeofences.isEmpty()) {
                    Log.w(TAG, "ENTER transition for ${triggeringGeofences.size} geofence(s) with no matching mosques — skipping DND enable")
                    return
                }

                // Require consensus across consecutive readings before enabling DND.
                // Each triggering geofence records an "inside" reading; the filter
                // requires minConsecutiveSamples (default 2) agreements before acting.
                val consensusReached = validGeofences.any { fence ->
                    filter.checkConsensus(fence.requestId, isInside = true)
                }

                LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "GEOFENCE_CONSENSUS", mapOf("isInside" to true, "reached" to consensusReached))

                if (!consensusReached) {
                    Log.w(TAG, "ENTER transition filtered — insufficient consensus (need consecutive samples), skipping DND enable")
                    return
                }

                if (PrayerAlarmPrefs.isAutoDNDEnabled()) {
                    val durationMinutes = PrayerAlarmPrefs.getAutoDNDDurationMin()
                    Log.d(TAG, "Consensus reached for ${validGeofences.size} geofence(s) — enabling DND with ${durationMinutes}min duration")
                    DNDController.enableDND(context, durationMinutes)
                } else {
                    Log.w(TAG, "Auto-DND is disabled in settings — skipping DND enable")
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "EXIT transition for ${triggeringGeofences.size} geofence(s)")

                val existingMosqueIds = MosqueRepository.getAllMosques().map { it.id }.toSet()
                val validGeofences = triggeringGeofences.filter { it.requestId in existingMosqueIds }

                if (validGeofences.isEmpty()) {
                    Log.w(TAG, "EXIT transition with no matching mosques — skipping DND disable")
                    return
                }

                // Require consensus before disabling DND too, preventing GPS-jitter EXIT events
                val consensusReached = validGeofences.any { fence ->
                    filter.checkConsensus(fence.requestId, isInside = false)
                }

                LogHelper.logTelemetry(LogHelper.TelemetryCategory.EXECUTION, "GEOFENCE_CONSENSUS", mapOf("isInside" to false, "reached" to consensusReached))

                if (!consensusReached) {
                    Log.w(TAG, "EXIT transition filtered — insufficient consensus (need consecutive samples), skipping DND disable")
                    return
                }

                Log.d(TAG, "Consensus reached — disabling DND")
                DNDController.disableDND(context)
            }
            else -> {
                Log.w(TAG, "Unknown geofence transition: $geofenceTransition")
            }
        }
    }
}
