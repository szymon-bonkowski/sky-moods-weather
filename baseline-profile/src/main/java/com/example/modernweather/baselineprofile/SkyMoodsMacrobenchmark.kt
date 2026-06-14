package com.example.modernweather.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkyMoodsMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun coldStartupToLocations() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.COLD,
            iterations = 5
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForLocationsScreen()
        }
    }

    @Test
    fun warmStartupToLocations() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForLocationsScreen()
        }
    }

    @Test
    fun navigateToWeatherDetail() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = frameMetrics(),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForLocationsScreen()
            }
        ) {
            device.openFirstLocation()
        }
    }

    @Test
    fun scrollWeatherDetailVertically() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = frameMetrics(),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForLocationsScreen()
                device.openFirstLocation()
            }
        ) {
            device.scrollWeatherDetailVertically()
        }
    }

    @Test
    fun scrollHourlyForecastHorizontally() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = frameMetrics(),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForLocationsScreen()
                device.openFirstLocation()
            }
        ) {
            device.scrollHourlyForecastHorizontally()
        }
    }

    @Test
    fun renderWeeklyChartAndOpenSettings() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = frameMetrics(),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForLocationsScreen()
                device.openFirstLocation()
            }
        ) {
            device.scrollToWeeklyChart()
            device.pressBack()
            device.openSettings()
        }
    }

    private fun frameMetrics() = listOf(
        FrameTimingMetric()
    )
}
