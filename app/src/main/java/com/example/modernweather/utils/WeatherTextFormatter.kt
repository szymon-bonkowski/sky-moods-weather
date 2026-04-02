package com.example.modernweather.utils

import android.content.Context
import com.example.modernweather.R
import com.example.modernweather.data.models.WeatherCondition
import kotlin.math.abs
import kotlin.math.roundToInt

object WeatherTextFormatter {

    fun temperatureComparison(context: Context, currentTemperature: Int, referenceTemperature: Double?): String {
        val reference = referenceTemperature?.roundToInt() ?: return context.getString(R.string.weather_temp_same_as_yesterday)
        val delta = currentTemperature - reference
        return when {
            delta > 0 -> context.getString(R.string.weather_temp_warmer_than_yesterday, delta)
            delta < 0 -> context.getString(R.string.weather_temp_colder_than_yesterday, abs(delta))
            else -> context.getString(R.string.weather_temp_same_as_yesterday)
        }
    }

    fun pressureTrend(context: Context, diffHpa: Double?): String {
        val diff = diffHpa ?: return context.getString(R.string.weather_pressure_stable)
        return when {
            diff > 0.8 -> context.getString(R.string.weather_pressure_rising)
            diff < -0.8 -> context.getString(R.string.weather_pressure_falling)
            else -> context.getString(R.string.weather_pressure_stable)
        }
    }

    fun windDirection(context: Context, degrees: Double): String {
        val normalized = ((degrees % 360) + 360) % 360
        return when {
            normalized < 22.5 || normalized >= 337.5 -> context.getString(R.string.wind_direction_north)
            normalized < 67.5 -> context.getString(R.string.wind_direction_northeast)
            normalized < 112.5 -> context.getString(R.string.wind_direction_east)
            normalized < 157.5 -> context.getString(R.string.wind_direction_southeast)
            normalized < 202.5 -> context.getString(R.string.wind_direction_south)
            normalized < 247.5 -> context.getString(R.string.wind_direction_southwest)
            normalized < 292.5 -> context.getString(R.string.wind_direction_west)
            else -> context.getString(R.string.wind_direction_northwest)
        }
    }

    fun visibility(context: Context, meters: Int): String {
        return if (meters >= 1000) {
            context.getString(R.string.visibility_km_format, (meters / 1000.0).roundToInt())
        } else {
            context.getString(R.string.visibility_m_format, meters)
        }
    }

    fun conditionLabel(context: Context, condition: WeatherCondition): String {
        val resId = when (condition) {
            WeatherCondition.DAY_SUNNY, WeatherCondition.NIGHT_CLEAR -> R.string.weather_condition_clear
            WeatherCondition.DAY_PARTLY_CLOUDY, WeatherCondition.NIGHT_PARTLY_CLOUDY -> R.string.weather_condition_partly_cloudy
            WeatherCondition.DAY_CLOUDY, WeatherCondition.NIGHT_CLOUDY -> R.string.weather_condition_cloudy
            WeatherCondition.DAY_RAIN_LIGHT, WeatherCondition.NIGHT_RAIN_LIGHT -> R.string.weather_condition_light_rain
            WeatherCondition.DAY_RAIN_MEDIUM, WeatherCondition.NIGHT_RAIN_MEDIUM -> R.string.weather_condition_rain
            WeatherCondition.DAY_RAIN_HEAVY -> R.string.weather_condition_heavy_rain
            WeatherCondition.DAY_SNOW, WeatherCondition.NIGHT_SNOW -> R.string.weather_condition_snow
            WeatherCondition.DAY_FOG, WeatherCondition.NIGHT_FOG -> R.string.weather_condition_fog
            WeatherCondition.DAY_FOG_CLOUDY -> R.string.weather_condition_foggy_cloudy
            WeatherCondition.DAY_THUNDERSTORM, WeatherCondition.NIGHT_THUNDERSTORM -> R.string.weather_condition_thunderstorm
            WeatherCondition.DAY_THUNDERSTORM_HEAVY -> R.string.weather_condition_heavy_thunderstorm
            WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT, WeatherCondition.NIGHT_THUNDERSTORM_RAIN_LIGHT -> R.string.weather_condition_thunderstorm_with_rain
            WeatherCondition.DAY_THUNDERSTORM_RAIN_MEDIUM, WeatherCondition.NIGHT_THUNDERSTORM_MEDIUM_RAIN -> R.string.weather_condition_thunderstorm_with_heavy_rain
            WeatherCondition.DAY_WIND, WeatherCondition.NIGHT_WIND -> R.string.weather_condition_windy
            WeatherCondition.DAY_WIND_CLOUDY -> R.string.weather_condition_windy_cloudy
        }
        return context.getString(resId)
    }
}
