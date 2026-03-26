package com.example.modernweather.nowcast.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.modernweather.nowcast.model.LocalRiskLevel
import com.example.modernweather.nowcast.model.NowcastAssessment
import com.example.modernweather.nowcast.model.NowcastSettings
import com.example.modernweather.nowcast.model.RawPressureSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.nowcastStore: DataStore<Preferences> by preferencesDataStore(name = "nowcast_store")

class NowcastRepository(context: Context) {
    private val dataStore = context.nowcastStore

    val settingsFlow: Flow<NowcastSettings> = dataStore.data.map { preferences ->
        NowcastSettings(
            monitoringEnabled = preferences[MONITORING_ENABLED_KEY] ?: true,
            notificationsEnabled = preferences[NOTIFICATIONS_ENABLED_KEY] ?: true,
            useTfliteModel = preferences[USE_TFLITE_KEY] ?: true,
            sampleIntervalMinutes = (preferences[SAMPLE_INTERVAL_MINUTES_KEY] ?: 10).coerceIn(5, 30),
            notificationCooldownMinutes = (preferences[NOTIFICATION_COOLDOWN_MINUTES_KEY] ?: 120).coerceIn(30, 720)
        )
    }

    val assessmentFlow: Flow<NowcastAssessment> = dataStore.data.map { preferences ->
        val risk = runCatching {
            LocalRiskLevel.valueOf(preferences[LAST_RISK_LEVEL_KEY] ?: LocalRiskLevel.LOW.name)
        }.getOrDefault(LocalRiskLevel.LOW)

        val modelScore = (preferences[MODEL_SCORE_KEY] ?: -1f).takeIf { it >= 0f }

        NowcastAssessment(
            evaluatedAtEpochMillis = preferences[LAST_EVALUATED_AT_KEY] ?: 0L,
            latestPressureHpa = (preferences[LATEST_PRESSURE_KEY] ?: -1f).takeIf { it >= 0f },
            pressureDrop3h = preferences[PRESSURE_DROP_3H_KEY] ?: 0f,
            slopeHpaPerHour = preferences[SLOPE_HPA_H_KEY] ?: 0f,
            sampleCount = preferences[SAMPLE_COUNT_KEY] ?: 0,
            heuristicScore = preferences[HEURISTIC_SCORE_KEY] ?: 0f,
            modelScore = modelScore,
            fusedScore = preferences[FUSED_SCORE_KEY] ?: 0f,
            riskLevel = risk,
            reason = preferences[REASON_KEY] ?: "Brak danych do oceny lokalnej tendencji barycznej.",
            monitoringEnabled = preferences[MONITORING_ENABLED_KEY] ?: true
        )
    }

    suspend fun getSettings(): NowcastSettings = settingsFlow.first()

    suspend fun updateMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { it[MONITORING_ENABLED_KEY] = enabled }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED_KEY] = enabled }
    }

    suspend fun updateUseTflite(enabled: Boolean) {
        dataStore.edit { it[USE_TFLITE_KEY] = enabled }
    }

    suspend fun updateSampleIntervalMinutes(intervalMinutes: Int) {
        dataStore.edit { it[SAMPLE_INTERVAL_MINUTES_KEY] = intervalMinutes.coerceIn(5, 30) }
    }

    suspend fun updateNotificationCooldownMinutes(cooldownMinutes: Int) {
        dataStore.edit { it[NOTIFICATION_COOLDOWN_MINUTES_KEY] = cooldownMinutes.coerceIn(30, 720) }
    }

    suspend fun getPressureHistory(): List<RawPressureSample> {
        val raw = dataStore.data.first()[PRESSURE_HISTORY_KEY].orEmpty()
        return decodeSamples(raw)
    }

    suspend fun appendPressureSample(sample: RawPressureSample): List<RawPressureSample> {
        var updated = emptyList<RawPressureSample>()
        dataStore.edit { preferences ->
            val current = decodeSamples(preferences[PRESSURE_HISTORY_KEY].orEmpty())
            updated = (current + sample)
                .sortedBy { it.timestampEpochMillis }
                .takeLast(MAX_HISTORY_SAMPLES)
            preferences[PRESSURE_HISTORY_KEY] = encodeSamples(updated)
        }
        return updated
    }

    suspend fun saveAssessment(assessment: NowcastAssessment) {
        dataStore.edit { preferences ->
            preferences[LAST_EVALUATED_AT_KEY] = assessment.evaluatedAtEpochMillis
            preferences[LATEST_PRESSURE_KEY] = assessment.latestPressureHpa ?: -1f
            preferences[PRESSURE_DROP_3H_KEY] = assessment.pressureDrop3h
            preferences[SLOPE_HPA_H_KEY] = assessment.slopeHpaPerHour
            preferences[SAMPLE_COUNT_KEY] = assessment.sampleCount
            preferences[HEURISTIC_SCORE_KEY] = assessment.heuristicScore
            preferences[MODEL_SCORE_KEY] = assessment.modelScore ?: -1f
            preferences[FUSED_SCORE_KEY] = assessment.fusedScore
            preferences[LAST_RISK_LEVEL_KEY] = assessment.riskLevel.name
            preferences[REASON_KEY] = assessment.reason
        }
    }

    suspend fun getLastNotificationEpochMillis(): Long = dataStore.data.first()[LAST_NOTIFICATION_TS_KEY] ?: 0L

    suspend fun recordNotificationSent(timestampEpochMillis: Long, riskLevel: LocalRiskLevel) {
        dataStore.edit { preferences ->
            preferences[LAST_NOTIFICATION_TS_KEY] = timestampEpochMillis
            preferences[LAST_NOTIFICATION_RISK_KEY] = riskLevel.name
        }
    }

    private fun encodeSamples(samples: List<RawPressureSample>): String {
        return samples.joinToString(separator = ";") { "${it.timestampEpochMillis},${it.pressureHpa}" }
    }

    private fun decodeSamples(raw: String): List<RawPressureSample> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";")
            .mapNotNull { token ->
                val parts = token.split(",")
                if (parts.size != 2) return@mapNotNull null
                val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
                val pressure = parts[1].toFloatOrNull() ?: return@mapNotNull null
                RawPressureSample(timestampEpochMillis = ts, pressureHpa = pressure)
            }
            .sortedBy { it.timestampEpochMillis }
            .takeLast(MAX_HISTORY_SAMPLES)
    }

    companion object {
        private const val MAX_HISTORY_SAMPLES = 288

        private val MONITORING_ENABLED_KEY = booleanPreferencesKey("nowcast_monitoring_enabled")
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("nowcast_notifications_enabled")
        private val USE_TFLITE_KEY = booleanPreferencesKey("nowcast_use_tflite")
        private val SAMPLE_INTERVAL_MINUTES_KEY = intPreferencesKey("nowcast_sample_interval_minutes")
        private val NOTIFICATION_COOLDOWN_MINUTES_KEY = intPreferencesKey("nowcast_notification_cooldown_minutes")

        private val PRESSURE_HISTORY_KEY = stringPreferencesKey("nowcast_pressure_history")
        private val LAST_EVALUATED_AT_KEY = longPreferencesKey("nowcast_last_eval_ts")
        private val LATEST_PRESSURE_KEY = floatPreferencesKey("nowcast_latest_pressure")
        private val PRESSURE_DROP_3H_KEY = floatPreferencesKey("nowcast_drop_3h")
        private val SLOPE_HPA_H_KEY = floatPreferencesKey("nowcast_slope_h")
        private val SAMPLE_COUNT_KEY = intPreferencesKey("nowcast_sample_count")
        private val HEURISTIC_SCORE_KEY = floatPreferencesKey("nowcast_heuristic_score")
        private val MODEL_SCORE_KEY = floatPreferencesKey("nowcast_model_score")
        private val FUSED_SCORE_KEY = floatPreferencesKey("nowcast_fused_score")
        private val LAST_RISK_LEVEL_KEY = stringPreferencesKey("nowcast_last_risk")
        private val REASON_KEY = stringPreferencesKey("nowcast_reason")

        private val LAST_NOTIFICATION_TS_KEY = longPreferencesKey("nowcast_last_notification_ts")
        private val LAST_NOTIFICATION_RISK_KEY = stringPreferencesKey("nowcast_last_notification_risk")
    }
}
