package com.example.modernweather.nowcast.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NowcastScheduler {
    private const val PERIODIC_WORK_NAME = "local-pressure-nowcast-periodic"
    private const val IMMEDIATE_WORK_NAME = "local-pressure-nowcast-immediate"
    private const val WORK_TAG = "local-pressure-nowcast"

    fun schedule(context: Context, intervalMinutes: Int, immediate: Boolean = false) {
        val clamped = intervalMinutes.coerceIn(15, 30)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<PressureNowcastWorker>(
            clamped.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        if (immediate) {
            val immediateRequest = OneTimeWorkRequestBuilder<PressureNowcastWorker>()
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()

            workManager.enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateRequest
            )
        }
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        workManager.cancelAllWorkByTag(WORK_TAG)
    }
}
