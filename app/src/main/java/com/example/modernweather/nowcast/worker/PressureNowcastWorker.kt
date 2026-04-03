package com.example.modernweather.nowcast.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.modernweather.R
import com.example.modernweather.data.repository.SettingsRepository
import com.example.modernweather.nowcast.analysis.KalmanPressureFilter
import com.example.modernweather.nowcast.analysis.NowcastEngine
import com.example.modernweather.nowcast.data.NowcastRepository
import com.example.modernweather.nowcast.ml.TfliteNowcastPredictor
import com.example.modernweather.nowcast.model.LocalRiskLevel
import com.example.modernweather.nowcast.model.NowcastAssessment
import com.example.modernweather.nowcast.model.RawPressureSample
import com.example.modernweather.utils.localized
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

class PressureNowcastWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        try {
            val repository = NowcastRepository(applicationContext)
            val settings = repository.getSettings()
            if (!settings.monitoringEnabled) return@withContext Result.success()
            if (!hasPressureSensor()) return@withContext Result.success()

            val pressureSample = readPressureOnce() ?: return@withContext Result.retry()
            val now = System.currentTimeMillis()
            val existingHistory = repository.getPressureHistory()
            val sanitizedPressure = sanitizePressureSample(
                newPressure = pressureSample,
                previousPressure = existingHistory.lastOrNull()?.pressureHpa
            ) ?: return@withContext Result.success()

            val history = repository.appendPressureSample(
                RawPressureSample(
                    timestampEpochMillis = now,
                    pressureHpa = sanitizedPressure
                )
            )

            val kalman = KalmanPressureFilter()
            val filtered = history.map {
                it.copy(pressureHpa = kalman.update(it.pressureHpa))
            }

            val predictor = if (settings.useTfliteModel) {
                TfliteNowcastPredictor.tryCreate(applicationContext)
            } else {
                null
            }
            val engine = NowcastEngine()
            val assessment = engine.evaluate(
                history = filtered,
                settings = settings,
                predictor = predictor
            )
            predictor?.close()

            repository.saveAssessment(assessment)
            maybeNotify(assessment, settings.notificationCooldownMinutes, repository)

            // Only reschedule if monitoring is still enabled AFTER all work is done
            // This prevents infinite reschedule loops
            val currentSettings = repository.getSettings()
            if (currentSettings.monitoringEnabled && !isStopped) {
                NowcastScheduler.schedule(
                    context = applicationContext,
                    intervalMinutes = currentSettings.sampleIntervalMinutes,
                    immediate = false
                )
            }

            Result.success()
        } catch (e: Exception) {
            // Log error but don't reschedule on failure to prevent infinite retry loops
            Result.failure()
        }
    }

    private fun hasPressureSensor(): Boolean {
        val sensorManager = ContextCompat.getSystemService(applicationContext, SensorManager::class.java)
        return sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }

    private fun sanitizePressureSample(
        newPressure: Float,
        previousPressure: Float?
    ): Float? {
        if (newPressure !in 870f..1085f) return null
        if (previousPressure == null) return newPressure
        val delta = abs(newPressure - previousPressure)
        if (delta > 3.0f) return null
        return newPressure
    }

    private suspend fun maybeNotify(
        assessment: NowcastAssessment,
        cooldownMinutes: Int,
        repository: NowcastRepository
    ) {
        if (assessment.riskLevel == LocalRiskLevel.LOW) return
        val settings = repository.getSettings()
        if (!settings.notificationsEnabled) return

        if (!hasPostNotificationPermission()) return

        val appLanguage = SettingsRepository(applicationContext).userSettingsFlow.first().appLanguage
        val localizedContext = applicationContext.localized(
            appLanguage.languageTag.takeIf { it.isNotBlank() }
        )
        val bodyText = assessment.latestPressureHpa?.let { pressure ->
            localizedContext.getString(
                R.string.nowcast_notification_body_format,
                "%.1f".format(pressure),
                "%.1f".format(assessment.pressureDrop3h)
            )
        } ?: localizedContext.getString(R.string.nowcast_notification_no_pressure)

        val now = System.currentTimeMillis()
        val lastSent = repository.getLastNotificationEpochMillis()
        val cooldownMs = cooldownMinutes * 60_000L
        if (now - lastSent < cooldownMs) return

        createChannelIfMissing()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(localizedContext.getString(R.string.nowcast_notification_title))
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = ContextCompat.getSystemService(applicationContext, NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
        repository.recordNotificationSent(now, assessment.riskLevel)
    }

    private fun hasPostNotificationPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannelIfMissing() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.nowcast_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = applicationContext.getString(R.string.nowcast_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private suspend fun readPressureOnce(): Float? = withTimeoutOrNull(4_000L) {
        suspendCancellableCoroutine { continuation ->
            val sensorManager = ContextCompat.getSystemService(applicationContext, SensorManager::class.java)
            val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

            if (sensorManager == null || pressureSensor == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = OneShotPressureListener(sensorManager, continuation)
            sensorManager.registerListener(listener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)

            continuation.invokeOnCancellation {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    private class OneShotPressureListener(
        private val sensorManager: SensorManager,
        private val continuation: CancellableContinuation<Float?>
    ) : SensorEventListener {
        @Volatile
        private var emitted = false

        override fun onSensorChanged(event: SensorEvent?) {
            if (emitted || event == null || event.values.isEmpty()) return
            emitted = true
            val value = event.values[0]
            sensorManager.unregisterListener(this)
            if (!continuation.isCompleted) {
                continuation.resume(value.takeIf { it in 800f..1100f })
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    companion object {
        private const val CHANNEL_ID = "local_nowcast_alerts"
        private const val NOTIFICATION_ID = 9211
    }
}
