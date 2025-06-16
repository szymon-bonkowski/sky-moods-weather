package com.example.modernweather.data.models

enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT
}

data class UserSettings(
    val temperatureUnit: TemperatureUnit
)

