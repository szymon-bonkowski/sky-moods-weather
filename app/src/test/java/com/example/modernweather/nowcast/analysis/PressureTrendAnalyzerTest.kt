package com.example.modernweather.nowcast.analysis

import com.example.modernweather.nowcast.model.RawPressureSample
import org.junit.Assert.assertEquals
import org.junit.Test

class PressureTrendAnalyzerTest {
    private val analyzer = PressureTrendAnalyzer()
    private val now = 10_000_000_000L

    @Test
    fun fewerThanTwoSamplesReturnsNeutralTrend() {
        val result = analyzer.analyze(listOf(sample(minutesAgo = 10, pressure = 1008f)), now)

        assertEquals(0f, result.slopeHpaPerHour, 0.001f)
        assertEquals(0f, result.pressureDrop1h, 0.001f)
        assertEquals(0f, result.pressureDrop3h, 0.001f)
        assertEquals(1, result.sampleCountInWindow)
    }

    @Test
    fun noSamplesInsideWindowReturnsNeutralTrend() {
        val result = analyzer.analyze(
            listOf(
                sample(minutesAgo = 240, pressure = 1010f),
                sample(minutesAgo = 220, pressure = 1009f)
            ),
            now
        )

        assertEquals(0f, result.slopeHpaPerHour, 0.001f)
        assertEquals(0f, result.pressureDrop1h, 0.001f)
        assertEquals(0f, result.pressureDrop3h, 0.001f)
        assertEquals(0, result.sampleCountInWindow)
    }

    @Test
    fun samplesOutsideWindowAreIgnored() {
        val result = analyzer.analyze(
            listOf(
                sample(minutesAgo = 240, pressure = 1015f),
                sample(minutesAgo = 120, pressure = 1010f),
                sample(minutesAgo = 0, pressure = 1008f)
            ),
            now
        )

        assertEquals(2f, result.pressureDrop3h, 0.001f)
        assertEquals(2, result.sampleCountInWindow)
    }

    @Test
    fun equalTimestampsReturnZeroRegressionSlope() {
        val timestamp = now - 10 * 60_000L
        val result = analyzer.analyze(
            listOf(
                RawPressureSample(timestamp, 1010f),
                RawPressureSample(timestamp, 1009f)
            ),
            now
        )

        assertEquals(0f, result.slopeHpaPerHour, 0.001f)
        assertEquals(1f, result.pressureDrop3h, 0.001f)
    }

    @Test
    fun risingPressureHasNegativeDropAndPositiveSlope() {
        val result = analyzer.analyze(
            listOf(
                sample(minutesAgo = 120, pressure = 1006f),
                sample(minutesAgo = 60, pressure = 1008f),
                sample(minutesAgo = 0, pressure = 1010f)
            ),
            now
        )

        assertEquals(-4f, result.pressureDrop3h, 0.001f)
        assertEquals(-2f, result.pressureDrop1h, 0.001f)
        assertEquals(2f, result.slopeHpaPerHour, 0.001f)
    }

    @Test
    fun fallingPressureHasPositiveDropAndNegativeSlope() {
        val result = analyzer.analyze(
            listOf(
                sample(minutesAgo = 120, pressure = 1010f),
                sample(minutesAgo = 60, pressure = 1008f),
                sample(minutesAgo = 0, pressure = 1006f)
            ),
            now
        )

        assertEquals(4f, result.pressureDrop3h, 0.001f)
        assertEquals(2f, result.pressureDrop1h, 0.001f)
        assertEquals(-2f, result.slopeHpaPerHour, 0.001f)
    }

    @Test
    fun irregularSpacingComputesWindowDrops() {
        val result = analyzer.analyze(
            listOf(
                sample(minutesAgo = 180, pressure = 1010f),
                sample(minutesAgo = 75, pressure = 1008.5f),
                sample(minutesAgo = 30, pressure = 1007.5f),
                sample(minutesAgo = 0, pressure = 1007f)
            ),
            now
        )

        assertEquals(3f, result.pressureDrop3h, 0.001f)
        assertEquals(0.5f, result.pressureDrop1h, 0.001f)
        assertEquals(4, result.sampleCountInWindow)
    }

    @Test
    fun oneHourDropUsesSamplesInsideLastHour() {
        val result = analyzer.analyze(
            listOf(
                sample(minutesAgo = 180, pressure = 1012f),
                sample(minutesAgo = 60, pressure = 1010f),
                sample(minutesAgo = 0, pressure = 1008f)
            ),
            now
        )

        assertEquals(2f, result.pressureDrop1h, 0.001f)
    }

    @Test
    fun threeHourDropUsesFirstAndLatestWindowSamples() {
        val result = analyzer.analyze(
            listOf(
                sample(minutesAgo = 180, pressure = 1014f),
                sample(minutesAgo = 120, pressure = 1012f),
                sample(minutesAgo = 0, pressure = 1008f)
            ),
            now
        )

        assertEquals(6f, result.pressureDrop3h, 0.001f)
    }

    @Test
    fun regressionSlopeMatchesLinearPressureChange() {
        val result = analyzer.analyze(
            listOf(
                sample(minutesAgo = 120, pressure = 1000f),
                sample(minutesAgo = 60, pressure = 999f),
                sample(minutesAgo = 0, pressure = 998f)
            ),
            now
        )

        assertEquals(-1f, result.slopeHpaPerHour, 0.001f)
    }

    private fun sample(minutesAgo: Long, pressure: Float): RawPressureSample {
        return RawPressureSample(
            timestampEpochMillis = now - minutesAgo * 60_000L,
            pressureHpa = pressure
        )
    }
}
