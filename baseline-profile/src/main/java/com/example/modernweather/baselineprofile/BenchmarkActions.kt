package com.example.modernweather.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.example.modernweather"

internal fun UiDevice.waitForLocationsScreen() {
    findByAny(
        By.textContains("locations"),
        By.textContains("lokalizacje"),
        By.textContains("ubicaciones")
    )
}

internal fun UiDevice.openFirstLocation() {
    val location = findByAny(
        By.textContains("Warsaw"),
        By.textContains("Warszawa"),
        By.textContains("Krakow"),
        By.textContains("Gdansk")
    )

    if (location != null) {
        location.click()
    } else {
        click(displayWidth / 2, (displayHeight * 0.42f).toInt())
    }
    waitForWeatherDetail()
}

internal fun UiDevice.waitForWeatherDetail() {
    findByAny(
        By.textContains("WEEKLY"),
        By.textContains("PROGNOZA"),
        By.textContains("DETAILS"),
        By.textContains("SZCZEG")
    )
}

internal fun UiDevice.scrollWeatherDetailVertically() {
    repeat(3) {
        swipe(displayWidth / 2, (displayHeight * 0.80f).toInt(), displayWidth / 2, (displayHeight * 0.25f).toInt(), 20)
        waitForIdle()
    }
    repeat(2) {
        swipe(displayWidth / 2, (displayHeight * 0.25f).toInt(), displayWidth / 2, (displayHeight * 0.80f).toInt(), 20)
        waitForIdle()
    }
}

internal fun UiDevice.scrollHourlyForecastHorizontally() {
    swipe((displayWidth * 0.85f).toInt(), (displayHeight * 0.45f).toInt(), (displayWidth * 0.15f).toInt(), (displayHeight * 0.45f).toInt(), 20)
    waitForIdle()
    swipe((displayWidth * 0.15f).toInt(), (displayHeight * 0.45f).toInt(), (displayWidth * 0.85f).toInt(), (displayHeight * 0.45f).toInt(), 20)
    waitForIdle()
}

internal fun UiDevice.openSettings() {
    val settings = findByAny(
        By.descContains("Settings"),
        By.descContains("Ustawienia"),
        By.textContains("Settings"),
        By.textContains("Ustawienia")
    )

    if (settings != null) {
        settings.click()
    } else {
        click(displayWidth - 96, 96)
    }
    findByAny(
        By.textContains("Units"),
        By.textContains("Jednostki"),
        By.textContains("Appearance"),
        By.textContains("Wygl")
    )
}

internal fun UiDevice.scrollToWeeklyChart() {
    findByAny(By.textContains("WEEKLY"), By.textContains("PROGNOZA")) ?: scrollWeatherDetailVertically()
}

private fun UiDevice.findByAny(vararg selectors: BySelector): UiObject2? {
    return selectors.firstNotNullOfOrNull { selector ->
        wait(Until.findObject(selector), 750L)
    }
}
