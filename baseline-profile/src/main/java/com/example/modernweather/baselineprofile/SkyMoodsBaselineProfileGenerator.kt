package com.example.modernweather.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkyMoodsBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        baselineProfileRule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            device.waitForLocationsScreen()
            device.openFirstLocation()
            device.scrollHourlyForecastHorizontally()
            device.scrollToWeeklyChart()
            device.scrollWeatherDetailVertically()
            device.pressBack()
            device.openSettings()
            device.pressBack()
        }
    }
}
