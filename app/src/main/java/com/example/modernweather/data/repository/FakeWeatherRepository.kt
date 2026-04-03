package com.example.modernweather.data.repository

import android.content.Context
import com.example.modernweather.R
import com.example.modernweather.data.models.*
import com.example.modernweather.utils.WeatherTextFormatter
import com.example.modernweather.utils.localized
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

class FakeWeatherRepository(
    private val context: Context
) : WeatherRepository {

    var testTime: LocalTime? = LocalTime.of(22, 15)
    var testSunrise: LocalTime? = LocalTime.of(5, 0)
    var testSunset: LocalTime? = LocalTime.of(22, 31)

    private val weatherDataCache = mutableMapOf<String, MutableStateFlow<WeatherData?>>()

    private fun resolveCurrentTime(): LocalTime {
        return testTime ?: LocalTime.now()
    }

    override fun getCurrentTime(): LocalTime {
        return resolveCurrentTime()
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

    override fun getSavedLocations(languageTag: String?): Flow<List<Location>> = flow {
        delay(300)
        emit(fakeLocations)
    }

    override fun getWeatherData(locationId: String, languageTag: String?): Flow<WeatherData> = flow {
        val localizedContext = context.localized(languageTag)
        delay(1500)
        val location = fakeLocations.first { it.id == locationId }

        // Emit initial data
        emit(generateFakeDataFor(location, localizedContext))

        // Cache the flow for this location
        val cacheFlow = weatherDataCache.getOrPut(locationId) {
            MutableStateFlow<WeatherData?>(null)
        }

        // Collect from cache for subsequent updates (if any)
        cacheFlow.collect { cached ->
            if (cached != null) {
                emit(cached)
            }
        }
    }

    private fun generateFakeDataFor(location: Location, localizedContext: Context): WeatherData {
        val now = resolveCurrentTime()
        val today = LocalDate.now()

        return WeatherData(
            location = location,
            currentWeather = CurrentWeather(
                temperature = 22,
                feelsLike = 25,
                highTemp = 26,
                lowTemp = 13,
                condition = WeatherTextFormatter.conditionLabel(localizedContext, WeatherCondition.DAY_PARTLY_CLOUDY),
                conditionEnum = WeatherCondition.DAY_PARTLY_CLOUDY,
                temperatureComparison = localizedContext.getString(R.string.weather_temp_warmer_than_yesterday, 2)
            ),
            alert = WeatherAlert(
                id = "alert1",
                title = localizedContext.getString(R.string.weather_alert_thunderstorm_title),
                description = localizedContext.getString(R.string.weather_alert_thunderstorm_description),
                severity = AlertSeverity.WARNING,
                expirationTime = localizedContext.getString(R.string.weather_alert_expires_at_time, "2:00")
            ),
            hourlyForecast = (-5..24).map { offset ->
                val time = now.withMinute(0).withSecond(0).withNano(0).plusHours(offset.toLong())
                val hour = time.hour
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
                    precipitationChance = if (hour in 0..3 || hour > 22) Random.nextInt(40, 90) else Random.nextInt(0, 15),
                    isCurrent = offset == 0
                )
            },
            dailyForecast = listOf(
                DailyForecast(today.plusDays(-7), 19, 11, WeatherCondition.DAY_RAIN_MEDIUM, 70),
                DailyForecast(today.plusDays(-6), 18, 10, WeatherCondition.DAY_CLOUDY, 45),
                DailyForecast(today.plusDays(-5), 20, 12, WeatherCondition.DAY_PARTLY_CLOUDY, 25),
                DailyForecast(today.plusDays(-4), 21, 12, WeatherCondition.DAY_SUNNY, 8),
                DailyForecast(today.plusDays(-3), 22, 13, WeatherCondition.DAY_WIND_CLOUDY, 15),
                DailyForecast(today.plusDays(-2), 21, 12, WeatherCondition.DAY_RAIN_LIGHT, 55),
                DailyForecast(today.plusDays(-1), 23, 14, WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT, 40),
                DailyForecast(today.plusDays(0), 26, 14, WeatherCondition.DAY_SUNNY, 10),
                DailyForecast(today.plusDays(1), 24, 13, WeatherCondition.DAY_RAIN_LIGHT, 90),
                DailyForecast(today.plusDays(2), 23, 15, WeatherCondition.DAY_THUNDERSTORM, 50),
                DailyForecast(today.plusDays(3), 22, 16, WeatherCondition.DAY_SNOW, 35),
                DailyForecast(today.plusDays(4), 25, 16, WeatherCondition.DAY_PARTLY_CLOUDY, 40),
                DailyForecast(today.plusDays(5), 26, 16, WeatherCondition.DAY_WIND_CLOUDY, 12),
                DailyForecast(today.plusDays(6), 24, 17, WeatherCondition.DAY_FOG, 5),
                DailyForecast(today.plusDays(7), 23, 15, WeatherCondition.DAY_RAIN_MEDIUM, 55),
                DailyForecast(today.plusDays(8), 22, 14, WeatherCondition.DAY_CLOUDY, 30),
                DailyForecast(today.plusDays(9), 24, 15, WeatherCondition.DAY_PARTLY_CLOUDY, 20),
                DailyForecast(today.plusDays(10), 25, 16, WeatherCondition.DAY_SUNNY, 8),
                DailyForecast(today.plusDays(11), 27, 17, WeatherCondition.DAY_WIND, 6),
                DailyForecast(today.plusDays(12), 26, 16, WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT, 45),
                DailyForecast(today.plusDays(13), 24, 15, WeatherCondition.DAY_RAIN_LIGHT, 60)
            ),
            weatherDetails = WeatherDetails(
                windSpeed = 15,
                windGusts = 25,
                windDirection = WeatherTextFormatter.windDirection(localizedContext, 135.0),
                humidity = 56,
                dewPoint = 13,
                pressure = 1013,
                pressureTrend = WeatherTextFormatter.pressureTrend(localizedContext, 0.0),
                uvIndex = if (now.hour in 8..18) 4 else 0,
                visibility = WeatherTextFormatter.visibility(localizedContext, 17_000),
                cloudCover = 75,
                airQualityIndex = 41,
                pm25 = 12.5f,
                pm10 = 24.3f,
                no2 = 18.7f,
                precipitation = 2.5f,
                grassPollen = PollenLevel.MEDIUM,
                treePollen = PollenLevel.LOW,
                ragweedPollen = PollenLevel.NONE
            ),
            sunInfo = SunInfo(
                sunrise = testSunrise ?: LocalTime.of(4, 20),
                sunset = testSunset ?: LocalTime.of(20, 47)
            )
        )
    }
}
