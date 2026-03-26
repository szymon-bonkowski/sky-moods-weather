package com.example.modernweather.nowcast.model

data class RawPressureSample(
    val timestampEpochMillis: Long,
    val pressureHpa: Float
)

data class FilteredPressureSample(
    val timestampEpochMillis: Long,
    val rawPressureHpa: Float,
    val filteredPressureHpa: Float
)

data class PressureTrendResult(
    val slopeHpaPerHour: Float,
    val pressureDrop3h: Float,
    val pressureDrop1h: Float,
    val sampleCountInWindow: Int
)

enum class LocalRiskLevel {
    LOW,
    ELEVATED,
    HIGH,
    SEVERE
}

data class NowcastSettings(
    val monitoringEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val useTfliteModel: Boolean = true,
    val sampleIntervalMinutes: Int = 10,
    val notificationCooldownMinutes: Int = 120
)

data class NowcastAssessment(
    val evaluatedAtEpochMillis: Long = 0L,
    val latestPressureHpa: Float? = null,
    val pressureDrop3h: Float = 0f,
    val slopeHpaPerHour: Float = 0f,
    val sampleCount: Int = 0,
    val heuristicScore: Float = 0f,
    val modelScore: Float? = null,
    val fusedScore: Float = 0f,
    val riskLevel: LocalRiskLevel = LocalRiskLevel.LOW,
    val reason: String = "Brak danych do oceny lokalnej tendencji barycznej.",
    val monitoringEnabled: Boolean = true
)
