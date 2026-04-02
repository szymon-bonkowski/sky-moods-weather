package com.example.modernweather.data.repository

import com.example.modernweather.data.models.Location
import com.example.modernweather.data.models.WeatherData
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface WeatherRepository {
    fun getSavedLocations(): Flow<List<Location>>
    fun getWeatherData(locationId: String): Flow<WeatherData>
    fun getCurrentTime(): LocalTime
}
