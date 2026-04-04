package com.example.modernweather.data.repository

import android.content.Context
import com.example.modernweather.R
import com.example.modernweather.data.models.AlertSeverity
import com.example.modernweather.data.models.CurrentWeather
import com.example.modernweather.data.models.DailyForecast
import com.example.modernweather.data.models.HourlyForecast
import com.example.modernweather.data.models.Location
import com.example.modernweather.data.models.PollenLevel
import com.example.modernweather.data.models.SunInfo
import com.example.modernweather.data.models.WeatherAlert
import com.example.modernweather.data.models.WeatherCondition
import com.example.modernweather.data.models.WeatherData
import com.example.modernweather.data.models.WeatherDetails
import com.example.modernweather.utils.WeatherTextFormatter
import com.example.modernweather.utils.locationNameForId
import com.example.modernweather.utils.localized
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
import kotlin.math.roundToInt

class OpenMeteoWeatherRepository(
    private val context: Context
) : WeatherRepository {

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
        LocationSpec(id = "warszawa", query = "Warsaw", isCurrentLocation = true),
        LocationSpec(id = "krakow", query = "Krakow", isCurrentLocation = false),
        LocationSpec(id = "gdansk", query = "Gdansk", isCurrentLocation = false)
    )

    private val locationCache = ConcurrentHashMap<String, Location>()
    private val geocodedCache = ConcurrentHashMap<String, GeocodedLocation>()
    @Volatile
    private var lastCurrentTime: LocalTime? = null

    override fun getSavedLocations(languageTag: String?): Flow<List<Location>> = flow {
        emit(locationSpecs.map { resolveLocation(it, languageTag) })
    }

    override fun getWeatherData(locationId: String, languageTag: String?): Flow<WeatherData> = flow {
        while (currentCoroutineContext().isActive) {
            emit(fetchWeatherData(locationId, languageTag))
            delay(15 * 60 * 1000L)
        }
    }

    override fun getCurrentTime(): LocalTime = lastCurrentTime ?: LocalTime.now()

    private suspend fun resolveLocation(spec: LocationSpec, languageTag: String?): Location {
        val cacheKey = locationCacheKey(spec.id, languageTag)
        locationCache[cacheKey]?.let { return it }

        val geocoded = geocodeLocation(spec, languageTag)
        val location = Location(
            id = spec.id,
            name = context.localized(languageTag).locationNameForId(spec.id, geocoded.name),
            isCurrentLocation = spec.isCurrentLocation
        )
        locationCache[cacheKey] = location
        geocodedCache[cacheKey] = geocoded
        return location
    }

    private suspend fun fetchWeatherData(locationId: String, languageTag: String?): WeatherData {
        val spec = locationSpecs.firstOrNull { it.id == locationId }
            ?: throw IllegalArgumentException("Unknown Open-Meteo location ID: $locationId")
        val cacheKey = locationCacheKey(spec.id, languageTag)
        val localizedContext = context.localized(languageTag)

        val geocoded = geocodedCache[cacheKey] ?: geocodeLocation(spec, languageTag).also {
            geocodedCache[cacheKey] = it
        }
        locationCache[cacheKey] = Location(
            id = spec.id,
            name = localizedContext.locationNameForId(spec.id, geocoded.name),
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
        val temperatureComparison = WeatherTextFormatter.temperatureComparison(
            context = localizedContext,
            currentTemperature = currentTemperature,
            referenceTemperature = previousDayTemp
        )

        val pressureTrend = WeatherTextFormatter.pressureTrend(
            context = localizedContext,
            diffHpa = buildPressureTrend(
                hourlyPressures = parseHourlyDoubles(hourly, "pressure_msl"),
                currentIndex = currentIndex
            )
        )

        val weatherDetails = WeatherDetails(
            windSpeed = currentWindSpeed,
            windGusts = currentWindGusts,
            windDirection = WeatherTextFormatter.windDirection(localizedContext, currentWindDirection),
            humidity = currentHumidity,
            dewPoint = currentDewPoint,
            pressure = currentPressure,
            pressureTrend = pressureTrend,
            uvIndex = currentUvIndex,
            visibility = WeatherTextFormatter.visibility(localizedContext, currentVisibilityMeters),
            cloudCover = currentCloudCover,
            airQualityIndex = airQuality.currentAqi,
            pm25 = airQuality.currentPm25,
            pm10 = airQuality.currentPm10,
            no2 = airQuality.currentNo2,
            precipitation = currentPrecipitation,
            grassPollen = airQuality.grassPollen,
            treePollen = airQuality.treePollen,
            ragweedPollen = airQuality.ragweedPollen
        )

        val alert = buildWeatherAlert(
            localizedContext = localizedContext,
            currentWeather = currentWeatherCondition,
            hourlyForecast = hourlyForecast,
            currentWindSpeed = currentWindSpeed,
            currentWindGusts = currentWindGusts,
            currentUvIndex = currentUvIndex
        )

        return WeatherData(
            location = locationCache[cacheKey] ?: Location(
                id = spec.id,
                name = localizedContext.locationNameForId(spec.id, geocoded.name),
                isCurrentLocation = spec.isCurrentLocation
            ),
            currentWeather = CurrentWeather(
                temperature = currentTemperature,
                feelsLike = currentFeelsLike,
                highTemp = todayForecast.highTemp,
                lowTemp = todayForecast.lowTemp,
                condition = WeatherTextFormatter.conditionLabel(localizedContext, currentWeatherCondition),
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
            append("&past_days=7")
            append("&past_hours=24")
            append("&forecast_hours=24")
            append("&forecast_days=14")
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
            append("&current=us_aqi,pm2_5,pm10,nitrogen_dioxide,grass_pollen,birch_pollen,ragweed_pollen")
            append("&timezone=auto")
            append("&forecast_days=1")
        }

        val json = requestJson(url)
        val current = json.getJSONObject("current")
        return AirQualityBundle(
            currentAqi = current.getDouble("us_aqi").roundToInt(),
            currentPm25 = current.getDouble("pm2_5").toFloat(),
            currentPm10 = current.getDouble("pm10").toFloat(),
            currentNo2 = current.getDouble("nitrogen_dioxide").toFloat(),
            grassPollen = mapPollenLevel(current.optDouble("grass_pollen", 0.0)),
            treePollen = mapPollenLevel(current.optDouble("birch_pollen", 0.0)),
            ragweedPollen = mapPollenLevel(current.optDouble("ragweed_pollen", 0.0))
        )
    }

    private fun mapPollenLevel(value: Double): PollenLevel {
        return when {
            value <= 0.5 -> PollenLevel.NONE
            value <= 15.0 -> PollenLevel.LOW
            value <= 50.0 -> PollenLevel.MEDIUM
            value <= 150.0 -> PollenLevel.HIGH
            else -> PollenLevel.VERY_HIGH
        }
    }

    private suspend fun geocodeLocation(spec: LocationSpec, languageTag: String?): GeocodedLocation {
        val languageCode = languageTag?.takeIf { it.isNotBlank() }?.let { java.util.Locale.forLanguageTag(it).language }
            ?: java.util.Locale.getDefault().language
        val url = buildString {
            append("https://geocoding-api.open-meteo.com/v1/search?")
            append("name=${encodeQuery(spec.query)}")
            append("&count=1")
            append("&language=$languageCode")
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
            DailyForecast(
                date = date,
                highTemp = highs.getOrNull(index)?.roundToInt() ?: 0,
                lowTemp = lows.getOrNull(index)?.roundToInt() ?: 0,
                conditionEnum = mapWeatherCode(weatherCodes.getOrNull(index) ?: 3, isDay = true),
                precipitationChance = precipitation.getOrNull(index) ?: 0
            )
        }.filter { forecast ->
            val distanceDays = java.time.temporal.ChronoUnit.DAYS.between(currentDate, forecast.date)
            distanceDays in -7L..13L
        }
    }

    private fun buildTemperatureComparison(
        currentTemperature: Int,
        referenceTemperature: Double?
    ): Double? {
        val reference = referenceTemperature?.roundToInt() ?: return null
        return (currentTemperature - reference).toDouble()
    }

    private fun buildPressureTrend(
        hourlyPressures: List<Double>,
        currentIndex: Int
    ): Double? {
        val currentPressure = hourlyPressures.getOrNull(currentIndex) ?: return null
        val threeHoursAgo = hourlyPressures.getOrNull((currentIndex - 3).coerceAtLeast(0))
            ?: return null
        return currentPressure - threeHoursAgo
    }

    private fun buildWeatherAlert(
        localizedContext: Context,
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
                title = localizedContext.getString(R.string.weather_alert_warning_title),
                description = localizedContext.getString(R.string.weather_alert_intense_rain_description),
                severity = AlertSeverity.SEVERE,
                expirationTime = severeHour.time.plusHours(3).toString()
            )
            severeHour != null -> WeatherAlert(
                id = "open-meteo-severe-weather",
                title = localizedContext.getString(R.string.weather_alert_warning_title),
                description = localizedContext.getString(R.string.weather_alert_rapid_change_description),
                severity = AlertSeverity.WARNING,
                expirationTime = severeHour.time.plusHours(3).toString()
            )
            windAlert -> WeatherAlert(
                id = "open-meteo-wind",
                title = localizedContext.getString(R.string.weather_alert_wind_title),
                description = localizedContext.getString(R.string.weather_alert_wind_description),
                severity = AlertSeverity.WARNING,
                expirationTime = localizedContext.getString(R.string.weather_alert_expires_in_hours)
            )
            uvAlert -> WeatherAlert(
                id = "open-meteo-uv",
                title = localizedContext.getString(R.string.weather_alert_uv_title),
                description = localizedContext.getString(R.string.weather_alert_uv_description),
                severity = AlertSeverity.INFO,
                expirationTime = localizedContext.getString(R.string.weather_alert_until_end_of_day)
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
                title = localizedContext.getString(R.string.weather_alert_storm_title),
                description = localizedContext.getString(R.string.weather_alert_storm_description),
                severity = AlertSeverity.WARNING,
                expirationTime = localizedContext.getString(R.string.weather_alert_now)
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
        val currentNo2: Float,
        val grassPollen: PollenLevel,
        val treePollen: PollenLevel,
        val ragweedPollen: PollenLevel
    )

    private fun locationCacheKey(locationId: String, languageTag: String?): String {
        return "${languageTag.orEmpty()}::$locationId"
    }
}
