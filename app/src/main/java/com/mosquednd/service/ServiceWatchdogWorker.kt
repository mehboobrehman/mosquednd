package com.mosquednd.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.mosquednd.util.LogHelper

class ServiceWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ServiceWatchdogWorker"
        private const val UNIQUE_WORK_NAME = "MosqueDNDServiceWatchdog"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            LogHelper.logd(TAG, "Watchdog worker scheduled to run every 15 minutes")
        }
    }

    override suspend fun doWork(): Result {
        LogHelper.logd(TAG, "Watchdog checking persistent monitoring service status...")
        try {
            PersistentMonitoringService.startService(applicationContext)
            return Result.success()
        } catch (e: Exception) {
            LogHelper.loge(TAG, "Error in watchdog worker starting service", e)
            return Result.failure()
        }
    }
}