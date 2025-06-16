package com.example.modernweather.data.models

import java.time.LocalDate
import java.time.LocalTime


enum class WeatherCondition {
    SUNNY, PARTLY_CLOUDY, CLOUDY, RAIN, HEAVY_RAIN, THUNDERSTORM, SNOW, FOG
}


enum class AlertSeverity {
    INFO, WARNING, SEVERE
}

data class Location(
    val id: String,
    val name: String,
    val isCurrentLocation: Boolean = false
)

data class CurrentWeather(
    val temperature: Int,
    val feelsLike: Int,
    val highTemp: Int,
    val lowTemp: Int,
    val condition: String,
    val conditionEnum: WeatherCondition
)

data class HourlyForecast(
    val time: LocalTime,
    val temperature: Int,
    val conditionEnum: WeatherCondition,
    val precipitationChance: Int
)

data class DailyForecast(
    val date: LocalDate,
    val highTemp: Int,
    val lowTemp: Int,
    val conditionEnum: WeatherCondition,
    val precipitationChance: Int
)

data class WeatherAlert(
    val id: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val expirationTime: String
)

data class WeatherDetails(
    val windSpeed: Int, // km/h
    val windGusts: Int, // km/h
    val windDirection: String,
    val humidity: Int, // w %
    val dewPoint: Int, // w °C
    val pressure: Int, // w hPa
    val pressureTrend: String, // "Rising", "Falling", "Steady"
    val uvIndex: Int,
    val visibility: String,
    val cloudCover: Int, // w %
    val airQualityIndex: Int
)

data class SunInfo(
    val sunrise: LocalTime,
    val sunset: LocalTime
)

