package com.example.modernweather.data.repository

import com.example.modernweather.data.models.AlertSeverity
import com.example.modernweather.data.models.CurrentWeather
import com.example.modernweather.data.models.DailyForecast
import com.example.modernweather.data.models.HourlyForecast
import com.example.modernweather.data.models.Location
import com.example.modernweather.data.models.SunInfo
import com.example.modernweather.data.models.WeatherAlert
import com.example.modernweather.data.models.WeatherCondition
import com.example.modernweather.data.models.WeatherData
import com.example.modernweather.data.models.WeatherDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

class OpenMeteoWeatherRepository : WeatherRepository {

    private data class LocationSpec(
        val id: String,
        val query: String,
        val isCurrentLocation: Boolean
    )

    private data class GeocodedLocation(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val timezone: String
    )

    private data class ForecastBundle(
        val current: JSONObject,
        val hourly: JSONObject,
        val daily: JSONObject
    )

    private val locationSpecs = listOf(
        LocationSpec(id = "warszawa", query = "Warszawa", isCurrentLocation = true),
        LocationSpec(id = "krakow", query = "Kraków", isCurrentLocation = false),
        LocationSpec(id = "gdansk", query = "Gdańsk", isCurrentLocation = false)
    )

    private val locationCache = ConcurrentHashMap<String, Location>()
    private val geocodedCache = ConcurrentHashMap<String, GeocodedLocation>()
    @Volatile
    private var lastCurrentTime: LocalTime? = null

    override fun getSavedLocations(): Flow<List<Location>> = flow {
        emit(locationSpecs.map { resolveLocation(it) })
    }

    override fun getWeatherData(locationId: String): Flow<WeatherData> = flow {
        while (currentCoroutineContext().isActive) {
            emit(fetchWeatherData(locationId))
            delay(15 * 60 * 1000L)
        }
    }

    override fun getCurrentTime(): LocalTime = lastCurrentTime ?: LocalTime.now()

    private suspend fun resolveLocation(spec: LocationSpec): Location {
        locationCache[spec.id]?.let { return it }

        val geocoded = geocodeLocation(spec)
        val location = Location(
            id = spec.id,
            name = geocoded.name,
            isCurrentLocation = spec.isCurrentLocation
        )
        locationCache[spec.id] = location
        geocodedCache[spec.id] = geocoded
        return location
    }

    private suspend fun fetchWeatherData(locationId: String): WeatherData {
        val spec = locationSpecs.firstOrNull { it.id == locationId }
            ?: throw IllegalArgumentException("Unknown Open-Meteo location ID: $locationId")

        val geocoded = geocodedCache[spec.id] ?: geocodeLocation(spec).also {
            geocodedCache[spec.id] = it
        }
        locationCache[spec.id] = Location(
            id = spec.id,
            name = geocoded.name,
            isCurrentLocation = spec.isCurrentLocation
        )

        val forecast = fetchForecastBundle(geocoded)
        val airQuality = fetchAirQualityBundle(geocoded)

        val current = forecast.current
        val hourly = forecast.hourly
        val daily = forecast.daily

        val currentTime = parseLocalDateTime(current.getString("time"))
        lastCurrentTime = currentTime.toLocalTime()
        val currentTemperature = current.getDouble("temperature_2m").roundToInt()
        val currentFeelsLike = current.getDouble("apparent_temperature").roundToInt()
        val currentWeatherCode = current.getInt("weather_code")
        val currentIsDay = current.getInt("is_day") == 1
        val currentWeatherCondition = mapWeatherCode(currentWeatherCode, currentIsDay)
        val currentCloudCover = current.getInt("cloud_cover")
        val currentWindSpeed = current.getDouble("wind_speed_10m").roundToInt()
        val currentWindGusts = current.getDouble("wind_gusts_10m").roundToInt()
        val currentWindDirection = current.getDouble("wind_direction_10m")
        val currentHumidity = current.getInt("relative_humidity_2m")
        val currentDewPoint = current.getDouble("dew_point_2m").roundToInt()
        val currentPressure = current.getDouble("pressure_msl").roundToInt()
        val currentVisibilityMeters = current.getDouble("visibility").roundToInt()
        val currentUvIndex = if (current.has("uv_index")) current.getDouble("uv_index").roundToInt() else 0
        val currentPrecipitation = if (current.has("precipitation")) current.getDouble("precipitation").toFloat() else 0f

        val hourlyTimes = parseHourlyTimes(hourly)
        val hourlyTemperatures = parseHourlyDoubles(hourly, "temperature_2m")
        val hourlyWeatherCodes = parseHourlyInts(hourly, "weather_code")
        val hourlyPrecipitation = parseHourlyInts(hourly, "precipitation_probability")
        val hourlyIsDay = parseHourlyInts(hourly, "is_day")

        val currentIndex = hourlyTimes.indexOfFirst { it == currentTime }.takeIf { it >= 0 }
            ?: hourlyTimes.indexOfFirst { !it.isBefore(currentTime) }.takeIf { it >= 0 }
            ?: 0

        val hourlyForecastStartIndex = (currentIndex - 5).coerceAtLeast(0)
        val hourlyForecast = buildHourlyForecast(
            times = hourlyTimes,
            temperatures = hourlyTemperatures,
            weatherCodes = hourlyWeatherCodes,
            precipitationChance = hourlyPrecipitation,
            isDayFlags = hourlyIsDay,
            startIndex = hourlyForecastStartIndex,
            count = hourlyTimes.size,
            currentIndex = currentIndex
        )

        val dailyForecast = buildDailyForecast(
            daily = daily,
            currentDate = currentTime.toLocalDate()
        )

        val todayForecast = dailyForecast.firstOrNull() ?: throw IOException("Open-Meteo daily forecast was empty")
        val previousDayTemp = hourlyTemperatures.getOrNull((currentIndex - 24).coerceAtLeast(0))
        val temperatureComparison = buildTemperatureComparison(
            currentTemperature = currentTemperature,
            referenceTemperature = previousDayTemp
        )

        val pressureTrend = buildPressureTrend(
            hourlyPressures = parseHourlyDoubles(hourly, "pressure_msl"),
            currentIndex = currentIndex
        )

        val weatherDetails = WeatherDetails(
            windSpeed = currentWindSpeed,
            windGusts = currentWindGusts,
            windDirection = toWindDirectionLabel(currentWindDirection),
            humidity = currentHumidity,
            dewPoint = currentDewPoint,
            pressure = currentPressure,
            pressureTrend = pressureTrend,
            uvIndex = currentUvIndex,
            visibility = formatVisibility(currentVisibilityMeters),
            cloudCover = currentCloudCover,
            airQualityIndex = airQuality.currentAqi,
            pm25 = airQuality.currentPm25,
            pm10 = airQuality.currentPm10,
            no2 = airQuality.currentNo2,
            precipitation = currentPrecipitation
        )

        val alert = buildWeatherAlert(
            currentWeather = currentWeatherCondition,
            hourlyForecast = hourlyForecast,
            currentWindSpeed = currentWindSpeed,
            currentWindGusts = currentWindGusts,
            currentUvIndex = currentUvIndex
        )

        return WeatherData(
            location = locationCache[spec.id] ?: Location(
                id = spec.id,
                name = geocoded.name,
                isCurrentLocation = spec.isCurrentLocation
            ),
            currentWeather = CurrentWeather(
                temperature = currentTemperature,
                feelsLike = currentFeelsLike,
                highTemp = todayForecast.highTemp,
                lowTemp = todayForecast.lowTemp,
                condition = conditionLabel(currentWeatherCondition),
                conditionEnum = currentWeatherCondition,
                temperatureComparison = temperatureComparison
            ),
            alert = alert,
            hourlyForecast = hourlyForecast,
            dailyForecast = dailyForecast,
            weatherDetails = weatherDetails,
            sunInfo = SunInfo(
                sunrise = parseLocalTime(daily.getJSONArray("sunrise").getString(findDailyIndex(daily, currentTime.toLocalDate()))),
                sunset = parseLocalTime(daily.getJSONArray("sunset").getString(findDailyIndex(daily, currentTime.toLocalDate())))
            )
        )
    }

    private suspend fun fetchForecastBundle(location: GeocodedLocation): ForecastBundle {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast?")
            append("latitude=${location.latitude}")
            append("&longitude=${location.longitude}")
            append("&current=temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,dew_point_2m,pressure_msl,cloud_cover,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,is_day,uv_index,precipitation")
            append("&hourly=temperature_2m,apparent_temperature,weather_code,precipitation_probability,relative_humidity_2m,dew_point_2m,pressure_msl,cloud_cover,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day")
            append("&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset")
            append("&past_hours=24")
            append("&forecast_hours=24")
            append("&forecast_days=7")
            append("&timezone=auto")
        }

        val json = requestJson(url)
        return ForecastBundle(
            current = json.getJSONObject("current"),
            hourly = json.getJSONObject("hourly"),
            daily = json.getJSONObject("daily")
        )
    }

    private suspend fun fetchAirQualityBundle(location: GeocodedLocation): AirQualityBundle {
        val url = buildString {
            append("https://air-quality-api.open-meteo.com/v1/air-quality?")
            append("latitude=${location.latitude}")
            append("&longitude=${location.longitude}")
            append("&current=us_aqi,pm2_5,pm10,nitrogen_dioxide")
            append("&timezone=auto")
            append("&forecast_days=1")
        }

        val json = requestJson(url)
        val current = json.getJSONObject("current")
        return AirQualityBundle(
            currentAqi = current.getDouble("us_aqi").roundToInt(),
            currentPm25 = current.getDouble("pm2_5").toFloat(),
            currentPm10 = current.getDouble("pm10").toFloat(),
            currentNo2 = current.getDouble("nitrogen_dioxide").toFloat()
        )
    }

    private suspend fun geocodeLocation(spec: LocationSpec): GeocodedLocation {
        val url = buildString {
            append("https://geocoding-api.open-meteo.com/v1/search?")
            append("name=${encodeQuery(spec.query)}")
            append("&count=1")
            append("&language=pl")
            append("&countryCode=PL")
        }

        val json = requestJson(url)
        val results = json.optJSONArray("results") ?: throw IOException("No Open-Meteo geocoding result for ${spec.query}")
        if (results.length() == 0) {
            throw IOException("No Open-Meteo geocoding result for ${spec.query}")
        }

        val result = results.getJSONObject(0)
        return GeocodedLocation(
            name = result.getString("name"),
            latitude = result.getDouble("latitude"),
            longitude = result.getDouble("longitude"),
            timezone = result.getString("timezone")
        )
    }

    private suspend fun requestJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("Open-Meteo request failed ($code): $body")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildHourlyForecast(
        times: List<LocalDateTime>,
        temperatures: List<Double>,
        weatherCodes: List<Int>,
        precipitationChance: List<Int>,
        isDayFlags: List<Int>,
        startIndex: Int,
        count: Int,
        currentIndex: Int
    ): List<HourlyForecast> {
        if (times.isEmpty()) return emptyList()

        val endIndex = (startIndex + count).coerceAtMost(times.size)
        return (startIndex until endIndex).map { index ->
            val isDay = isDayFlags.getOrNull(index) == 1
            HourlyForecast(
                time = times[index].toLocalTime(),
                temperature = temperatures.getOrNull(index)?.roundToInt() ?: 0,
                conditionEnum = mapWeatherCode(weatherCodes.getOrNull(index) ?: 3, isDay),
                precipitationChance = precipitationChance.getOrNull(index) ?: 0,
                isCurrent = index == currentIndex
            )
        }
    }

    private fun buildDailyForecast(
        daily: JSONObject,
        currentDate: LocalDate
    ): List<DailyForecast> {
        val times = parseDailyDates(daily)
        val weatherCodes = parseDailyInts(daily, "weather_code")
        val highs = parseDailyDoubles(daily, "temperature_2m_max")
        val lows = parseDailyDoubles(daily, "temperature_2m_min")
        val precipitation = parseDailyInts(daily, "precipitation_probability_max")

        return times.mapIndexedNotNull { index, date ->
            if (date < currentDate) return@mapIndexedNotNull null
            DailyForecast(
                date = date,
                highTemp = highs.getOrNull(index)?.roundToInt() ?: 0,
                lowTemp = lows.getOrNull(index)?.roundToInt() ?: 0,
                conditionEnum = mapWeatherCode(weatherCodes.getOrNull(index) ?: 3, isDay = true),
                precipitationChance = precipitation.getOrNull(index) ?: 0
            )
        }.take(7)
    }

    private fun buildTemperatureComparison(
        currentTemperature: Int,
        referenceTemperature: Double?
    ): String {
        val reference = referenceTemperature?.roundToInt() ?: return "Jak wczoraj"
        val delta = currentTemperature - reference
        return when {
            delta > 0 -> "Cieplej o ${delta}° niż wczoraj"
            delta < 0 -> "Chłodniej o ${abs(delta)}° niż wczoraj"
            else -> "Podobnie jak wczoraj"
        }
    }

    private fun buildPressureTrend(
        hourlyPressures: List<Double>,
        currentIndex: Int
    ): String {
        val currentPressure = hourlyPressures.getOrNull(currentIndex) ?: return "Stabilne"
        val threeHoursAgo = hourlyPressures.getOrNull((currentIndex - 3).coerceAtLeast(0))
            ?: return "Stabilne"
        val diff = currentPressure - threeHoursAgo
        return when {
            diff > 0.8 -> "Rosnące"
            diff < -0.8 -> "Spadające"
            else -> "Stabilne"
        }
    }

    private fun buildWeatherAlert(
        currentWeather: WeatherCondition,
        hourlyForecast: List<HourlyForecast>,
        currentWindSpeed: Int,
        currentWindGusts: Int,
        currentUvIndex: Int
    ): WeatherAlert? {
        val severeHour = hourlyForecast.firstOrNull { forecast ->
            forecast.conditionEnum in setOf(
                WeatherCondition.DAY_THUNDERSTORM,
                WeatherCondition.DAY_THUNDERSTORM_HEAVY,
                WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT,
                WeatherCondition.DAY_THUNDERSTORM_RAIN_MEDIUM,
                WeatherCondition.NIGHT_THUNDERSTORM,
                WeatherCondition.NIGHT_THUNDERSTORM_RAIN_LIGHT,
                WeatherCondition.NIGHT_THUNDERSTORM_MEDIUM_RAIN,
                WeatherCondition.DAY_RAIN_HEAVY,
                WeatherCondition.NIGHT_RAIN_MEDIUM,
                WeatherCondition.DAY_SNOW,
                WeatherCondition.NIGHT_SNOW
            ) || forecast.precipitationChance >= 80
        }

        val windAlert = currentWindSpeed >= 35 || currentWindGusts >= 50
        val uvAlert = currentUvIndex >= 7

        return when {
            severeHour != null && severeHour.precipitationChance >= 80 -> WeatherAlert(
                id = "open-meteo-severe-rain",
                title = "Ostrzeżenie pogodowe",
                description = "Prognoza wskazuje na intensywne opady lub burze w najbliższych godzinach.",
                severity = AlertSeverity.SEVERE,
                expirationTime = severeHour.time.plusHours(3).toString()
            )
            severeHour != null -> WeatherAlert(
                id = "open-meteo-severe-weather",
                title = "Ostrzeżenie pogodowe",
                description = "W najbliższych godzinach możliwe są gwałtowne zmiany pogody.",
                severity = AlertSeverity.WARNING,
                expirationTime = severeHour.time.plusHours(3).toString()
            )
            windAlert -> WeatherAlert(
                id = "open-meteo-wind",
                title = "Silny wiatr",
                description = "Open-Meteo wskazuje na silniejsze podmuchy wiatru.",
                severity = AlertSeverity.WARNING,
                expirationTime = "w ciągu kilku godzin"
            )
            uvAlert -> WeatherAlert(
                id = "open-meteo-uv",
                title = "Wysoki indeks UV",
                description = "Dzisiejszy indeks UV sugeruje ograniczenie ekspozycji na słońce.",
                severity = AlertSeverity.INFO,
                expirationTime = "do końca dnia"
            )
            currentWeather in setOf(
                WeatherCondition.DAY_THUNDERSTORM,
                WeatherCondition.DAY_THUNDERSTORM_HEAVY,
                WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT,
                WeatherCondition.DAY_THUNDERSTORM_RAIN_MEDIUM,
                WeatherCondition.NIGHT_THUNDERSTORM,
                WeatherCondition.NIGHT_THUNDERSTORM_RAIN_LIGHT,
                WeatherCondition.NIGHT_THUNDERSTORM_MEDIUM_RAIN
            ) -> WeatherAlert(
                id = "open-meteo-current-storm",
                title = "Burza w okolicy",
                description = "Aktualne dane pogodowe wskazują na burzowe warunki.",
                severity = AlertSeverity.WARNING,
                expirationTime = "teraz"
            )
            else -> null
        }
    }

    private fun parseHourlyTimes(hourly: JSONObject): List<LocalDateTime> {
        val array = hourly.getJSONArray("time")
        return (0 until array.length()).map { index ->
            parseLocalDateTime(array.getString(index))
        }
    }

    private fun parseDailyDates(daily: JSONObject): List<LocalDate> {
        val array = daily.getJSONArray("time")
        return (0 until array.length()).map { index ->
            LocalDate.parse(array.getString(index))
        }
    }

    private fun parseHourlyDoubles(objectNode: JSONObject, key: String): List<Double> {
        val array = objectNode.getJSONArray(key)
        return (0 until array.length()).map { index -> array.getDouble(index) }
    }

    private fun parseHourlyInts(objectNode: JSONObject, key: String): List<Int> {
        val array = objectNode.getJSONArray(key)
        return (0 until array.length()).map { index -> array.getInt(index) }
    }

    private fun parseDailyDoubles(objectNode: JSONObject, key: String): List<Double> {
        val array = objectNode.getJSONArray(key)
        return (0 until array.length()).map { index -> array.getDouble(index) }
    }

    private fun parseDailyInts(objectNode: JSONObject, key: String): List<Int> {
        val array = objectNode.getJSONArray(key)
        return (0 until array.length()).map { index -> array.getInt(index) }
    }

    private fun findDailyIndex(daily: JSONObject, date: LocalDate): Int {
        val times = parseDailyDates(daily)
        return times.indexOf(date).takeIf { it >= 0 } ?: 0
    }

    private fun parseLocalDateTime(value: String): LocalDateTime = LocalDateTime.parse(value)

    private fun parseLocalTime(value: String): LocalTime {
        return parseLocalDateTime(value).toLocalTime()
    }

    private fun encodeQuery(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun formatVisibility(meters: Int): String {
        return if (meters >= 1000) {
            "${(meters / 1000.0).roundToInt()} km"
        } else {
            "$meters m"
        }
    }

    private fun toWindDirectionLabel(degrees: Double): String {
        val normalized = ((degrees % 360) + 360) % 360
        return when {
            normalized < 22.5 || normalized >= 337.5 -> "Północny"
            normalized < 67.5 -> "Północno-wschodni"
            normalized < 112.5 -> "Wschodni"
            normalized < 157.5 -> "Południowo-wschodni"
            normalized < 202.5 -> "Południowy"
            normalized < 247.5 -> "Południowo-zachodni"
            normalized < 292.5 -> "Zachodni"
            else -> "Północno-zachodni"
        }
    }

    private fun conditionLabel(condition: WeatherCondition): String {
        return when (condition) {
            WeatherCondition.DAY_SUNNY, WeatherCondition.NIGHT_CLEAR -> "Bezchmurnie"
            WeatherCondition.DAY_PARTLY_CLOUDY, WeatherCondition.NIGHT_PARTLY_CLOUDY -> "Częściowo pochmurno"
            WeatherCondition.DAY_CLOUDY, WeatherCondition.NIGHT_CLOUDY -> "Pochmurno"
            WeatherCondition.DAY_RAIN_LIGHT, WeatherCondition.NIGHT_RAIN_LIGHT -> "Lekki deszcz"
            WeatherCondition.DAY_RAIN_MEDIUM, WeatherCondition.NIGHT_RAIN_MEDIUM -> "Deszcz"
            WeatherCondition.DAY_RAIN_HEAVY -> "Ulewa"
            WeatherCondition.DAY_SNOW, WeatherCondition.NIGHT_SNOW -> "Śnieg"
            WeatherCondition.DAY_FOG, WeatherCondition.NIGHT_FOG, WeatherCondition.DAY_FOG_CLOUDY -> "Mgła"
            WeatherCondition.DAY_THUNDERSTORM, WeatherCondition.NIGHT_THUNDERSTORM -> "Burza"
            WeatherCondition.DAY_THUNDERSTORM_HEAVY -> "Silna burza"
            WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT, WeatherCondition.NIGHT_THUNDERSTORM_RAIN_LIGHT -> "Burza z deszczem"
            WeatherCondition.DAY_THUNDERSTORM_RAIN_MEDIUM, WeatherCondition.NIGHT_THUNDERSTORM_MEDIUM_RAIN -> "Burza z ulewnym deszczem"
            WeatherCondition.DAY_WIND, WeatherCondition.NIGHT_WIND -> "Wietrznie"
            WeatherCondition.DAY_WIND_CLOUDY -> "Wietrznie i pochmurno"
            WeatherCondition.DAY_FOG_CLOUDY -> "Mgliście i pochmurno"
        }
    }

    private fun mapWeatherCode(code: Int, isDay: Boolean): WeatherCondition {
        return when (code) {
            0 -> if (isDay) WeatherCondition.DAY_SUNNY else WeatherCondition.NIGHT_CLEAR
            1 -> if (isDay) WeatherCondition.DAY_PARTLY_CLOUDY else WeatherCondition.NIGHT_PARTLY_CLOUDY
            2 -> if (isDay) WeatherCondition.DAY_PARTLY_CLOUDY else WeatherCondition.NIGHT_PARTLY_CLOUDY
            3 -> if (isDay) WeatherCondition.DAY_CLOUDY else WeatherCondition.NIGHT_CLOUDY
            45, 48 -> if (isDay) WeatherCondition.DAY_FOG else WeatherCondition.NIGHT_FOG
            51, 53, 55, 56, 57 -> if (isDay) WeatherCondition.DAY_RAIN_LIGHT else WeatherCondition.NIGHT_RAIN_LIGHT
            61, 66, 80 -> if (isDay) WeatherCondition.DAY_RAIN_LIGHT else WeatherCondition.NIGHT_RAIN_LIGHT
            63, 81 -> if (isDay) WeatherCondition.DAY_RAIN_MEDIUM else WeatherCondition.NIGHT_RAIN_MEDIUM
            65, 67, 82 -> if (isDay) WeatherCondition.DAY_RAIN_HEAVY else WeatherCondition.NIGHT_RAIN_MEDIUM
            71, 77, 85 -> if (isDay) WeatherCondition.DAY_SNOW else WeatherCondition.NIGHT_SNOW
            73 -> if (isDay) WeatherCondition.DAY_SNOW else WeatherCondition.NIGHT_SNOW
            75, 86 -> if (isDay) WeatherCondition.DAY_SNOW else WeatherCondition.NIGHT_SNOW
            95 -> if (isDay) WeatherCondition.DAY_THUNDERSTORM else WeatherCondition.NIGHT_THUNDERSTORM
            96, 99 -> if (isDay) WeatherCondition.DAY_THUNDERSTORM_HEAVY else WeatherCondition.NIGHT_THUNDERSTORM
            else -> {
                val fallback = if (isDay) WeatherCondition.DAY_CLOUDY else WeatherCondition.NIGHT_CLOUDY
                if (code in listOf(80, 81, 82) && isDay) WeatherCondition.DAY_RAIN_LIGHT else fallback
            }
        }
    }

    private data class AirQualityBundle(
        val currentAqi: Int,
        val currentPm25: Float,
        val currentPm10: Float,
        val currentNo2: Float
    )
}
