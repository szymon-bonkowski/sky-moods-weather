package com.example.modernweather.nowcast.analysis

import com.example.modernweather.nowcast.model.LocalRiskLevel
import com.example.modernweather.nowcast.model.PressureTrendResult

class HeuristicRiskScorer {

    data class ScoreResult(
        val score: Float,
        val riskLevel: LocalRiskLevel,
        val reason: String
    )

    fun score(
        trend: PressureTrendResult,
        latestPressureHpa: Float?
    ): ScoreResult {
        if (trend.sampleCountInWindow < 6 || latestPressureHpa == null) {
            return ScoreResult(
                score = 0f,
                riskLevel = LocalRiskLevel.LOW,
                reason = "Za mało próbek barometru do wiarygodnej oceny."
            )
        }

        var score = 0f
        val reasons = mutableListOf<String>()

        val drop3h = trend.pressureDrop3h
        when {
            drop3h >= 3.5f -> {
                score += 0.75f
                reasons += "Silny spadek ciśnienia ${"%.1f".format(drop3h)} hPa / 3h."
            }
            drop3h >= 2.0f -> {
                score += 0.55f
                reasons += "Wyraźny spadek ciśnienia ${"%.1f".format(drop3h)} hPa / 3h."
            }
            drop3h >= 1.0f -> {
                score += 0.30f
                reasons += "Umiarkowany spadek ciśnienia ${"%.1f".format(drop3h)} hPa / 3h."
            }
        }

        val slope = trend.slopeHpaPerHour
        when {
            slope <= -1.2f -> {
                score += 0.45f
                reasons += "Tempo spadku ciśnienia ${"%.2f".format(slope)} hPa/h."
            }
            slope <= -0.7f -> {
                score += 0.25f
                reasons += "Utrzymujący się trend spadkowy ${"%.2f".format(slope)} hPa/h."
            }
        }

        val oneHourDrop = trend.pressureDrop1h
        when {
            oneHourDrop >= 1.2f -> {
                score += 0.35f
                reasons += "Nagły spadek ciśnienia ${"%.1f".format(oneHourDrop)} hPa / 1h."
            }
            oneHourDrop >= 0.6f -> {
                score += 0.15f
                reasons += "Przyspieszający spadek ciśnienia ${"%.1f".format(oneHourDrop)} hPa / 1h."
            }
        }

        if (latestPressureHpa < 1000f) {
            score += 0.10f
            reasons += "Niskie ciśnienie absolutne (${latestPressureHpa.toInt()} hPa)."
        }

        score = score.coerceIn(0f, 1f)

        val risk = when {
            score >= 0.8f -> LocalRiskLevel.SEVERE
            score >= 0.6f -> LocalRiskLevel.HIGH
            score >= 0.35f -> LocalRiskLevel.ELEVATED
            else -> LocalRiskLevel.LOW
        }

        val reason = if (reasons.isEmpty()) {
            "Brak istotnych oznak szybkiego załamania pogody."
        } else {
            reasons.joinToString(" ")
        }

        return ScoreResult(score = score, riskLevel = risk, reason = reason)
    }
}
