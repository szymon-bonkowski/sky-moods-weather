package com.example.modernweather.data.repository

import com.example.modernweather.data.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

class FakeWeatherRepository : WeatherRepository {

    private val fakeLocations = listOf(
        Location(id = "warszawa", name = "Warszawa", isCurrentLocation = true),
        Location(id = "krakow", name = "Kraków"),
        Location(id = "gdansk", name = "Gdańsk")
    )

    override fun getSavedLocations(): Flow<List<Location>> = flow {
        delay(300)
        emit(fakeLocations)
    }

    override fun getWeatherData(locationId: String): Flow<WeatherData> = flow {
        delay(1500)
        val location = fakeLocations.first { it.id == locationId }
        emit(generateFakeDataFor(location))
    }

    private fun generateFakeDataFor(location: Location): WeatherData {
        val now = LocalTime.now()
        val today = LocalDate.now()

        return WeatherData(
            location = location,
            currentWeather = CurrentWeather(
                temperature = 22,
                feelsLike = 25,
                highTemp = 26,
                lowTemp = 13,
                condition = "Przeważnie pochmurno",
                conditionEnum = WeatherCondition.PARTLY_CLOUDY
            ),
            alert = WeatherAlert(
                id = "alert1",
                title = "Ostrzeżenie o burzach",
                description = "Możliwe gwałtowne burze z silnym wiatrem i gradem.",
                severity = AlertSeverity.WARNING,
                expirationTime = "wygasa o 2:00"
            ),
            hourlyForecast = (0..23).map { hour ->
                val time = LocalTime.of(hour, 0)
                HourlyForecast(
                    time = time,
                    temperature = when (hour) {
                        in 0..5 -> 13 + hour / 2
                        in 6..14 -> 16 + (hour - 6)
                        in 15..21 -> 26 - (hour - 14)
                        else -> 20 - (hour - 21)
                    },
                    conditionEnum = when (hour) {
                        in 0..2 -> WeatherCondition.RAIN
                        in 7..16 -> WeatherCondition.SUNNY
                        in 17..20 -> WeatherCondition.PARTLY_CLOUDY
                        else -> WeatherCondition.CLOUDY
                    },
                    precipitationChance = if (hour in 0..3 || hour > 22) Random.nextInt(40, 90) else Random.nextInt(0, 15)
                )
            },
            dailyForecast = (0..6).map { day ->
                val date = today.plusDays(day.toLong())
                DailyForecast(
                    date = date,
                    highTemp = 24 + Random.nextInt(-2, 3),
                    lowTemp = 15 + Random.nextInt(-2, 2),
                    conditionEnum = WeatherCondition.entries.random(),
                    precipitationChance = Random.nextInt(10, 60)
                )
            },
            weatherDetails = WeatherDetails(
                windSpeed = 15,
                windGusts = 25,
                windDirection = "Południowo-wschodni",
                humidity = 56,
                dewPoint = 13,
                pressure = 1013,
                pressureTrend = "Stabilne",
                uvIndex = if (now.hour in 8..18) 4 else 0,
                visibility = "17 km",
                cloudCover = 75,
                airQualityIndex = 42
            ),
            sunInfo = SunInfo(
                sunrise = LocalTime.of(4, 20),
                sunset = LocalTime.of(20, 47)
            )
        )
    }
}

