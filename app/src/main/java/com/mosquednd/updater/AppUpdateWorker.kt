package com.mosquednd.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AppUpdateWorker"
        private const val UNIQUE_WORK_NAME = "MosqueDNDAppUpdate"

        fun schedule(context: Context) {
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<AppUpdateWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            ).build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val updateManager = AppUpdateManager(applicationContext)
            val pair = updateManager.checkForUpdate()
            if (pair != null) {
                val (_, isNewer) = pair
                if (isNewer) {
                    // Background check found newer release.
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
