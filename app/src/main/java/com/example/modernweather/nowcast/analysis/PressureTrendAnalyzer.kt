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
        var oneHourFirst: RawPressureSample? = null
        var oneHourLatest: RawPressureSample? = null
        var sumX = 0f
        var sumY = 0f
        var sumXY = 0f
        var sumXX = 0f
        val firstTs = first.timestampEpochMillis

        inWindow.forEach { sample ->
            if (sample.timestampEpochMillis in (nowEpochMillis - oneHourMillis)..nowEpochMillis) {
                if (oneHourFirst == null) {
                    oneHourFirst = sample
                }
                oneHourLatest = sample
            }

            val x = (sample.timestampEpochMillis - firstTs).toFloat() / 3_600_000f
            val y = sample.pressureHpa
            sumX += x
            sumY += y
            sumXY += x * y
            sumXX += x * x
        }

        val pressureDrop1h = if (oneHourFirst != null && oneHourFirst != oneHourLatest) {
            oneHourFirst!!.pressureHpa - oneHourLatest!!.pressureHpa
        } else {
            0f
        }

        val n = inWindow.size.toFloat()
        val denominator = n * sumXX - sumX * sumX
        val slopePerHour = if (abs(denominator) < 1e-6f) {
            0f
        } else {
            (n * sumXY - sumX * sumY) / denominator
        }

        return PressureTrendResult(
            slopeHpaPerHour = slopePerHour,
            pressureDrop3h = pressureDrop3h,
            pressureDrop1h = pressureDrop1h,
            sampleCountInWindow = inWindow.size
        )
    }
}
