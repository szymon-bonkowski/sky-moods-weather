package com.example.modernweather.data.models

enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT
}

enum class WeatherDataSource {
    FAKE,
    OPEN_METEO
}

data class UserSettings(
    val temperatureUnit: TemperatureUnit,
    val isSystemTheme: Boolean,
    val isDarkTheme: Boolean,
    val weatherDataSource: WeatherDataSource = WeatherDataSource.FAKE
)
