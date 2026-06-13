package com.example.modernweather.data.models

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime


enum class WeatherCondition {
    DAY_SUNNY,
    DAY_PARTLY_CLOUDY,
    DAY_CLOUDY,
    DAY_RAIN_LIGHT,
    DAY_RAIN_MEDIUM,
    DAY_RAIN_HEAVY,
    DAY_SNOW,
    DAY_FOG,
    DAY_FOG_CLOUDY,
    DAY_THUNDERSTORM,
    DAY_THUNDERSTORM_HEAVY,
    DAY_THUNDERSTORM_RAIN_LIGHT,
    DAY_THUNDERSTORM_RAIN_MEDIUM,
    DAY_WIND,
    DAY_WIND_CLOUDY,

    NIGHT_CLEAR,
    NIGHT_PARTLY_CLOUDY,
    NIGHT_CLOUDY,
    NIGHT_RAIN_LIGHT,
    NIGHT_RAIN_MEDIUM,
    NIGHT_SNOW,
    NIGHT_FOG,
    NIGHT_THUNDERSTORM,
    NIGHT_THUNDERSTORM_RAIN_LIGHT,
    NIGHT_THUNDERSTORM_MEDIUM_RAIN,
    NIGHT_WIND
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
    val conditionEnum: WeatherCondition,
    val temperatureComparison: String = ""
)

data class HourlyForecast(
    val time: LocalTime,
    val temperature: Int,
    val conditionEnum: WeatherCondition,
    val precipitationChance: Int,
    val isCurrent: Boolean = false
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
@Immutable
data class WeatherDetails(
    val windSpeed: Int,
    val windGusts: Int,
    val windDirection: String,
    val humidity: Int,
    val dewPoint: Int,
    val pressure: Int,
    val pressureTrend: String,
    val uvIndex: Int,
    val visibility: String,
    val cloudCover: Int,
    val airQualityIndex: Int,
    val pm25: Float,
    val pm10: Float,
    val no2: Float,
    val precipitation: Float,
    val grassPollen: PollenLevel = PollenLevel.NONE,
    val treePollen: PollenLevel = PollenLevel.NONE,
    val ragweedPollen: PollenLevel = PollenLevel.NONE,
    val airQualityAvailable: Boolean = true,
    val pollenAvailable: Boolean = true
)

enum class PollenLevel {
    NONE, LOW, MEDIUM, HIGH, VERY_HIGH
}

data class SunInfo(
    val sunrise: LocalTime,
    val sunset: LocalTime
)
