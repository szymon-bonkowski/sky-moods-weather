package com.example.modernweather.nowcast.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NowcastScheduler {
    private const val UNIQUE_WORK_NAME = "local-pressure-nowcast"

    fun schedule(context: Context, intervalMinutes: Int, immediate: Boolean = false) {
        val clamped = intervalMinutes.coerceIn(5, 30)
        val delayMinutes = if (immediate) 0 else clamped
        val request = OneTimeWorkRequestBuilder<PressureNowcastWorker>()
            .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
