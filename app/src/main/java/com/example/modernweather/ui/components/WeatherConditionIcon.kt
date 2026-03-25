package com.example.modernweather.ui.components

import androidx.annotation.DrawableRes
import com.example.modernweather.R
import com.example.modernweather.data.models.WeatherCondition

@DrawableRes
fun weatherConditionIconRes(
    condition: WeatherCondition,
    isDay: Boolean = condition.name.startsWith("DAY")
): Int {
    return if (isDay) {
        when (condition) {
            WeatherCondition.DAY_SUNNY -> R.drawable.day
            WeatherCondition.DAY_PARTLY_CLOUDY -> R.drawable.cloudy_day
            WeatherCondition.DAY_CLOUDY -> R.drawable.cloudy
            WeatherCondition.DAY_RAIN_LIGHT -> R.drawable.light_rain_day
            WeatherCondition.DAY_RAIN_MEDIUM -> R.drawable.medium_rain_day
            WeatherCondition.DAY_RAIN_HEAVY -> R.drawable.heavy_rain
            WeatherCondition.DAY_SNOW -> R.drawable.snow_day
            WeatherCondition.DAY_FOG -> R.drawable.fog_day
            WeatherCondition.DAY_FOG_CLOUDY -> R.drawable.fog_cloudy
            WeatherCondition.DAY_THUNDERSTORM -> R.drawable.thunderstorm_day
            WeatherCondition.DAY_THUNDERSTORM_HEAVY -> R.drawable.thunderstorm_heavy_rain
            WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT -> R.drawable.thunderstorm_light_rain_day
            WeatherCondition.DAY_THUNDERSTORM_RAIN_MEDIUM -> R.drawable.thunderstorm_medium_rain_day
            WeatherCondition.DAY_WIND -> R.drawable.wind_day
            WeatherCondition.DAY_WIND_CLOUDY -> R.drawable.wind_cloudy
            else -> R.drawable.cloudy
        }
    } else {
        when (condition) {
            WeatherCondition.NIGHT_CLEAR -> R.drawable.night
            WeatherCondition.NIGHT_PARTLY_CLOUDY -> R.drawable.cloudy_night
            WeatherCondition.NIGHT_CLOUDY -> R.drawable.cloudy_night
            WeatherCondition.NIGHT_RAIN_LIGHT -> R.drawable.light_rain_night
            WeatherCondition.NIGHT_RAIN_MEDIUM -> R.drawable.medium_rain_night
            WeatherCondition.NIGHT_SNOW -> R.drawable.snow_night
            WeatherCondition.NIGHT_FOG -> R.drawable.fog_night
            WeatherCondition.NIGHT_THUNDERSTORM -> R.drawable.thunderstorm_night
            WeatherCondition.NIGHT_THUNDERSTORM_RAIN_LIGHT -> R.drawable.thunderstorm_light_rain_night
            WeatherCondition.NIGHT_THUNDERSTORM_MEDIUM_RAIN -> R.drawable.thunderstorm_medium_rain_night
            WeatherCondition.NIGHT_WIND -> R.drawable.wind_night
            else -> R.drawable.cloudy_night
        }
    }
}
