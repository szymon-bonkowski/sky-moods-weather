package com.example.modernweather.nowcast.analysis

import com.example.modernweather.nowcast.model.PressureTrendResult
import com.example.modernweather.nowcast.model.RawPressureSample
import kotlin.math.abs

class PressureTrendAnalyzer {

    fun analyze(
        samples: List<RawPressureSample>,
        nowEpochMillis: Long
    ): PressureTrendResult {
        if (samples.size < 2) {
            return PressureTrendResult(
                slopeHpaPerHour = 0f,
                pressureDrop3h = 0f,
                pressureDrop1h = 0f,
                sampleCountInWindow = samples.size
            )
        }

        val threeHoursMillis = 3 * 60 * 60 * 1000L
        val oneHourMillis = 60 * 60 * 1000L
        val windowStart = nowEpochMillis - threeHoursMillis
        val inWindow = samples.filter { it.timestampEpochMillis in windowStart..nowEpochMillis }
            .sortedBy { it.timestampEpochMillis }

        if (inWindow.size < 2) {
            return PressureTrendResult(
                slopeHpaPerHour = 0f,
                pressureDrop3h = 0f,
                pressureDrop1h = 0f,
                sampleCountInWindow = inWindow.size
            )
        }

        val first = inWindow.first()
        val latest = inWindow.last()
        val pressureDrop3h = first.pressureHpa - latest.pressureHpa
        val pressureDrop1h = computeDropInWindow(inWindow, nowEpochMillis - oneHourMillis, nowEpochMillis)

        val slopePerHour = linearRegressionSlopePerHour(inWindow)

        return PressureTrendResult(
            slopeHpaPerHour = slopePerHour,
            pressureDrop3h = pressureDrop3h,
            pressureDrop1h = pressureDrop1h,
            sampleCountInWindow = inWindow.size
        )
    }

    private fun computeDropInWindow(
        samples: List<RawPressureSample>,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Float {
        val within = samples.filter { it.timestampEpochMillis in startEpochMillis..endEpochMillis }
        if (within.size < 2) return 0f
        return within.first().pressureHpa - within.last().pressureHpa
    }

    private fun linearRegressionSlopePerHour(samples: List<RawPressureSample>): Float {
        val n = samples.size.toFloat()
        val firstTs = samples.first().timestampEpochMillis

        val x = samples.map { ((it.timestampEpochMillis - firstTs).toFloat() / 3_600_000f) }
        val y = samples.map { it.pressureHpa }

        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { (xi, yi) -> (xi * yi).toDouble() }.toFloat()
        val sumXX = x.sumOf { (it * it).toDouble() }.toFloat()

        val denominator = n * sumXX - sumX * sumX
        if (abs(denominator) < 1e-6f) return 0f

        return (n * sumXY - sumX * sumY) / denominator
    }
}
