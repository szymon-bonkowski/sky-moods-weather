package com.example.modernweather.data.repository

import com.example.modernweather.data.models.Location
import com.example.modernweather.data.models.WeatherData
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalTime

interface WeatherRepository {
    fun getSavedLocations(languageTag: String? = null): Flow<List<Location>>
    fun getWeatherData(locationId: String, languageTag: String? = null): Flow<WeatherData>
    fun isWeatherDataFresh(locationId: String, languageTag: String? = null): Boolean = false
    fun nextWeatherRefreshInstant(locationId: String, languageTag: String? = null): Instant? = null
    fun getCurrentTime(): LocalTime
}
