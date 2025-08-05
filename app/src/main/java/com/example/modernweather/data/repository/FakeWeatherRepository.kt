package com.example.modernweather.data.repository

import com.example.modernweather.data.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

class FakeWeatherRepository : WeatherRepository {

    var testTime: LocalTime? = LocalTime.of(22, 15)
    var testSunrise: LocalTime? = LocalTime.of(5, 0)
    var testSunset: LocalTime? = LocalTime.of(22, 31)

    private fun getCurrentTime(): LocalTime {
        return testTime ?: LocalTime.now()
    }

    fun setTestValues(currentTime: LocalTime, sunrise: LocalTime, sunset: LocalTime) {
        testTime = currentTime
        testSunrise = sunrise
        testSunset = sunset
    }

    fun resetToRealTime() {
        testTime = null
        testSunrise = null
        testSunset = null
    }

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

        while (true) {
            emit(generateFakeDataFor(location))
            delay(1000L)
            testTime = (testTime ?: LocalTime.now()).plusMinutes(1)
        }
    }

    private fun generateFakeDataFor(location: Location): WeatherData {
        val now = getCurrentTime()
        val today = LocalDate.now()

        return WeatherData(
            location = location,
            currentWeather = CurrentWeather(
                temperature = 22,
                feelsLike = 25,
                highTemp = 26,
                lowTemp = 13,
                condition = "Przeważnie pochmurno",
                conditionEnum = WeatherCondition.DAY_PARTLY_CLOUDY
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
                        in 0..2 -> WeatherCondition.NIGHT_RAIN_LIGHT
                        in 3..6 -> WeatherCondition.DAY_CLOUDY
                        in 7..16 -> WeatherCondition.DAY_SUNNY
                        in 17..20 -> WeatherCondition.DAY_PARTLY_CLOUDY
                        else -> WeatherCondition.DAY_CLOUDY
                    },
                    precipitationChance = if (hour in 0..3 || hour > 22) Random.nextInt(40, 90) else Random.nextInt(0, 15)
                )
            },
            dailyForecast = listOf(
                DailyForecast(today.plusDays(0), 26, 14, WeatherCondition.DAY_SUNNY, 10),
                DailyForecast(today.plusDays(1), 24, 13, WeatherCondition.DAY_RAIN_LIGHT, 90),
                DailyForecast(today.plusDays(2), 23, 15, WeatherCondition.DAY_THUNDERSTORM, 50),
                DailyForecast(today.plusDays(3), 22, 16, WeatherCondition.DAY_SNOW, 35),
                DailyForecast(today.plusDays(4), 25, 16, WeatherCondition.DAY_PARTLY_CLOUDY, 40),
                DailyForecast(today.plusDays(5), 26, 16, WeatherCondition.DAY_WIND_CLOUDY, 12),
                DailyForecast(today.plusDays(6), 24, 17, WeatherCondition.DAY_FOG, 5)
            ),
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
                airQualityIndex = 41
            ),
            sunInfo = SunInfo(
                sunrise = testSunrise ?: LocalTime.of(4, 20),
                sunset = testSunset ?: LocalTime.of(20, 47)
            )
        )
    }
}