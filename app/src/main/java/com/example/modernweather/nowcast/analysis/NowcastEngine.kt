package com.example.modernweather.nowcast.analysis

import com.example.modernweather.nowcast.ml.TfliteNowcastPredictor
import com.example.modernweather.nowcast.model.LocalRiskLevel
import com.example.modernweather.nowcast.model.NowcastAssessment
import com.example.modernweather.nowcast.model.NowcastSettings
import com.example.modernweather.nowcast.model.RawPressureSample

class NowcastEngine(
    private val trendAnalyzer: PressureTrendAnalyzer = PressureTrendAnalyzer(),
    private val heuristicRiskScorer: HeuristicRiskScorer = HeuristicRiskScorer()
) {

    fun evaluate(
        history: List<RawPressureSample>,
        settings: NowcastSettings,
        predictor: TfliteNowcastPredictor?
    ): NowcastAssessment {
        if (!settings.monitoringEnabled) {
            return NowcastAssessment(
                evaluatedAtEpochMillis = System.currentTimeMillis(),
                monitoringEnabled = false,
                reason = "Monitoring lokalny jest wyłączony."
            )
        }

        val now = System.currentTimeMillis()
        val sorted = history.sortedBy { it.timestampEpochMillis }
        val latest = sorted.lastOrNull()

        val trend = trendAnalyzer.analyze(sorted, now)
        val heuristic = heuristicRiskScorer.score(trend, latest?.pressureHpa)

        val modelScore = if (settings.useTfliteModel) {
            runCatching { predictor?.predictStormProbability(sorted) }.getOrNull()
        } else {
            null
        }

        val fusedScore = fuseScores(
            heuristicScore = heuristic.score,
            modelScore = modelScore
        )

        val risk = when {
            fusedScore >= 0.82f -> LocalRiskLevel.SEVERE
            fusedScore >= 0.62f -> LocalRiskLevel.HIGH
            fusedScore >= 0.38f -> LocalRiskLevel.ELEVATED
            else -> LocalRiskLevel.LOW
        }

        val reason = buildReason(heuristic.reason, modelScore, risk)

        return NowcastAssessment(
            evaluatedAtEpochMillis = now,
            latestPressureHpa = latest?.pressureHpa,
            pressureDrop3h = trend.pressureDrop3h,
            slopeHpaPerHour = trend.slopeHpaPerHour,
            sampleCount = sorted.size,
            heuristicScore = heuristic.score,
            modelScore = modelScore,
            fusedScore = fusedScore,
            riskLevel = risk,
            reason = reason,
            monitoringEnabled = true
        )
    }

    private fun fuseScores(
        heuristicScore: Float,
        modelScore: Float?
    ): Float {
        val clampedHeuristic = heuristicScore.coerceIn(0f, 1f)
        val clampedModel = modelScore?.coerceIn(0f, 1f)

        return if (clampedModel == null) {
            clampedHeuristic
        } else {
            (0.6f * clampedHeuristic + 0.4f * clampedModel).coerceIn(0f, 1f)
        }
    }

    private fun buildReason(
        heuristicReason: String,
        modelScore: Float?,
        riskLevel: LocalRiskLevel
    ): String {
        val modelHint = modelScore?.let {
            "Model lokalny ocenia prawdopodobieństwo opadów/burzy na ${(it * 100).toInt()}%."
        } ?: "Model lokalny niedostępny — ocena oparta na trendzie barycznym."

        val riskText = when (riskLevel) {
            LocalRiskLevel.LOW -> "Ryzyko niskie."
            LocalRiskLevel.ELEVATED -> "Ryzyko podwyższone."
            LocalRiskLevel.HIGH -> "Ryzyko wysokie."
            LocalRiskLevel.SEVERE -> "Ryzyko bardzo wysokie."
        }

        return "$riskText $heuristicReason $modelHint"
    }
}
