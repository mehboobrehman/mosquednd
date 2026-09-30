package com.mosquednd.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.mosquednd.data.Mosque
import com.mosquednd.MosqueDNDApp
import com.mosquednd.data.MosqueRepository
import com.mosquednd.geofence.GeofenceConfig
import com.mosquednd.receiver.GeofenceBroadcastReceiver
import com.mosquednd.util.PendingIntentConstants
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GeofenceService(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "GeofenceService"

        const val ACTION_ADD_GEOFENCE = "add_geofence"
        const val ACTION_REMOVE_GEOFENCE = "remove_geofence"
        const val ACTION_RE_REGISTER_ALL = "re_register_all"

        // Data keys
        private const val KEY_ACTION = "action"
        private const val KEY_MOSQUE_ID = "mosque_id"
        private const val KEY_MOSQUE_NAME = "mosque_name"
        private const val KEY_MOSQUE_LAT = "mosque_lat"
        private const val KEY_MOSQUE_LNG = "mosque_lng"
        private const val KEY_MOSQUE_RADIUS = "mosque_radius"
        private const val KEY_MOSQUE_ADDRESS = "mosque_address"

        // Unique work names to avoid duplicates
        private const val WORK_ADD_PREFIX = "geofence_add_"
        private const val WORK_REMOVE_PREFIX = "geofence_remove_"
        private const val WORK_RE_REGISTER = "geofence_re_register"

        // Geofence PendingIntent action
        private const val GEOFENCE_ACTION = "com.mosquednd.ACTION_GEOFENCE_EVENT"

        fun enqueueAddGeofence(context: Context, mosque: Mosque) {
            val data = Data.Builder()
                .putString(KEY_ACTION, ACTION_ADD_GEOFENCE)
                .putString(KEY_MOSQUE_ID, mosque.id)
                .putString(KEY_MOSQUE_NAME, mosque.name)
                .putDouble(KEY_MOSQUE_LAT, mosque.latitude)
                .putDouble(KEY_MOSQUE_LNG, mosque.longitude)
                .putFloat(KEY_MOSQUE_RADIUS, mosque.geofenceRadius)
                .putString(KEY_MOSQUE_ADDRESS, mosque.address)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<GeofenceService>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .beginUniqueWork(WORK_ADD_PREFIX + mosque.id, ExistingWorkPolicy.REPLACE, workRequest)
                .enqueue()

            Log.d(TAG, "Enqueued add geofence work for ${mosque.name}")
        }

        fun enqueueRemoveGeofence(context: Context, mosqueId: String) {
            val data = Data.Builder()
                .putString(KEY_ACTION, ACTION_REMOVE_GEOFENCE)
                .putString(KEY_MOSQUE_ID, mosqueId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<GeofenceService>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .beginUniqueWork(WORK_REMOVE_PREFIX + mosqueId, ExistingWorkPolicy.REPLACE, workRequest)
                .enqueue()

            Log.d(TAG, "Enqueued remove geofence work for $mosqueId")
        }

        fun enqueueReRegisterAll(context: Context) {
            val data = Data.Builder()
                .putString(KEY_ACTION, ACTION_RE_REGISTER_ALL)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<GeofenceService>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .beginUniqueWork(WORK_RE_REGISTER, ExistingWorkPolicy.REPLACE, workRequest)
                .enqueue()

            Log.d(TAG, "Enqueued re-register all geofences work")
        }
    }

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION)
        Log.d(TAG, "doWork: action=$action")
        com.mosquednd.util.LogHelper.logTelemetry(com.mosquednd.util.LogHelper.TelemetryCategory.EXECUTION, "GEOFENCE_SERVICE_WORK", mapOf("action" to (action ?: "null")))

        return when (action) {
            ACTION_ADD_GEOFENCE -> {
                val mosque = Mosque(
                    id = inputData.getString(KEY_MOSQUE_ID) ?: return Result.failure(),
                    name = inputData.getString(KEY_MOSQUE_NAME) ?: "",
                    latitude = inputData.getDouble(KEY_MOSQUE_LAT, 0.0),
                    longitude = inputData.getDouble(KEY_MOSQUE_LNG, 0.0),
                    address = inputData.getString(KEY_MOSQUE_ADDRESS) ?: "",
                    geofenceRadius = inputData.getFloat(KEY_MOSQUE_RADIUS, 200f)
                )
                addGeofence(mosque)
            }
            ACTION_REMOVE_GEOFENCE -> {
                val mosqueId = inputData.getString(KEY_MOSQUE_ID) ?: return Result.failure()
                removeGeofence(mosqueId)
            }
            ACTION_RE_REGISTER_ALL -> {
                reRegisterAllGeofences()
            }
            else -> {
                Log.w(TAG, "Unknown action: $action")
                Result.failure()
            }
        }
    }

    private suspend fun addGeofence(mosque: Mosque): Result {
        val radius = MosqueDNDApp.prefs.getInt("pref_detection_radius_m", GeofenceConfig.DEFAULT_DETECTION_RADIUS_M).toFloat()
        Log.d(TAG, "Adding geofence for ${mosque.name} with radius=${radius}m (from settings)")
        com.mosquednd.util.LogHelper.logTelemetry(com.mosquednd.util.LogHelper.TelemetryCategory.EXECUTION, "GEOFENCE_ADD", mapOf("mosque" to mosque.name, "id" to mosque.id, "radius" to radius))
        val geofence = Geofence.Builder()
            .setRequestId(mosque.id)
            .setCircularRegion(mosque.latitude, mosque.longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = getGeofencePendingIntent()

        return try {
            val task = LocationServices.getGeofencingClient(applicationContext)
                .addGeofences(request, pendingIntent)

            suspendCancellableCoroutine { cont ->
                task.addOnSuccessListener {
                    Log.d(TAG, "Geofence added for ${mosque.name}")
                    cont.resume(Result.success())
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to add geofence for ${mosque.name}", e)
                    cont.resume(Result.failure())
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException adding geofence for ${mosque.name}", e)
            Result.failure()
        }
    }

    private suspend fun removeGeofence(mosqueId: String): Result {
        com.mosquednd.util.LogHelper.logTelemetry(com.mosquednd.util.LogHelper.TelemetryCategory.EXECUTION, "GEOFENCE_REMOVE", mapOf("id" to mosqueId))
        return try {
            suspendCancellableCoroutine { cont ->
                LocationServices.getGeofencingClient(applicationContext)
                    .removeGeofences(listOf(mosqueId))
                    .addOnSuccessListener {
                        Log.d(TAG, "Geofence removed for $mosqueId")
                        cont.resume(Result.success())
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to remove geofence for $mosqueId", e)
                        cont.resume(Result.failure())
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception removing geofence for $mosqueId", e)
            Result.failure()
        }
    }

    private suspend fun reRegisterAllGeofences(): Result {
        val mosques = MosqueRepository.getAllMosques()
        if (mosques.isEmpty()) {
            Log.d(TAG, "No saved mosques to re-register — skipping")
            return Result.success()
        }

        Log.d(TAG, "Re-registering ${mosques.size} geofences after boot")

        val radius = MosqueDNDApp.prefs.getInt("pref_detection_radius_m", GeofenceConfig.DEFAULT_DETECTION_RADIUS_M).toFloat()
        Log.d(TAG, "Re-registering geofences with radius=${radius}m (from settings)")
        val geofences = mosques.map { mosque ->
            Geofence.Builder()
                .setRequestId(mosque.id)
                .setCircularRegion(mosque.latitude, mosque.longitude, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        val pendingIntent = getGeofencePendingIntent()

        return try {
            suspendCancellableCoroutine { cont ->
                LocationServices.getGeofencingClient(applicationContext)
                    .addGeofences(request, pendingIntent)
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully re-registered ${mosques.size} geofences")
                        cont.resume(Result.success())
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to re-register geofences", e)
                        cont.resume(Result.failure())
                    }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException re-registering geofences — location permission missing", e)
            Result.failure()
        }
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(GEOFENCE_ACTION).setClass(applicationContext, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(applicationContext, PendingIntentConstants.GEOFENCE, intent, flags)
    }
}
