package com.example.modernweather.data.models

data class WeatherData(
    val location: Location,
    val currentWeather: CurrentWeather,
    val alert: WeatherAlert?,
    val hourlyForecast: List<HourlyForecast>,
    val dailyForecast: List<DailyForecast>,
    val weatherDetails: WeatherDetails,
    val sunInfo: SunInfo
)


