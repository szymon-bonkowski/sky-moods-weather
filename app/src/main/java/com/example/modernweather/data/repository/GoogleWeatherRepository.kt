package com.example.modernweather.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.modernweather.BuildConfig
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

class GoogleWeatherRepository(
    private val context: Context
) : WeatherRepository {

    private data class LocationSpec(
        val id: String,
        val fallbackName: String,
        val latitude: Double,
        val longitude: Double,
        val isCurrentLocation: Boolean
    )

    private data class AirQualityBundle(
        val currentAqi: Int,
        val currentPm25: Float,
        val currentPm10: Float,
        val currentNo2: Float,
        val available: Boolean
    ) {
        companion object {
            val Unavailable = AirQualityBundle(
                currentAqi = 0,
                currentPm25 = 0f,
                currentPm10 = 0f,
                currentNo2 = 0f,
                available = false
            )
        }
    }

    private data class PollenBundle(
        val grassPollen: PollenLevel,
        val treePollen: PollenLevel,
        val ragweedPollen: PollenLevel,
        val available: Boolean
    ) {
        companion object {
            val Unavailable = PollenBundle(
                grassPollen = PollenLevel.NONE,
                treePollen = PollenLevel.NONE,
                ragweedPollen = PollenLevel.NONE,
                available = false
            )
        }
    }

    private val locationSpecs = listOf(
        LocationSpec(
            id = "warszawa",
            fallbackName = "Warsaw",
            latitude = 52.2297,
            longitude = 21.0122,
            isCurrentLocation = true
        ),
        LocationSpec(
            id = "krakow",
            fallbackName = "Krakow",
            latitude = 50.0647,
            longitude = 19.9450,
            isCurrentLocation = false
        ),
        LocationSpec(
            id = "gdansk",
            fallbackName = "Gdansk",
            latitude = 54.3520,
            longitude = 18.6466,
            isCurrentLocation = false
        )
    )

    private val locationCache = ConcurrentHashMap<String, Location>()

    @Volatile
    private var lastCurrentTime: LocalTime? = null

    override fun getSavedLocations(languageTag: String?): Flow<List<Location>> = flow {
        val localizedContext = context.localized(languageTag)
        emit(locationSpecs.map { spec -> resolveLocation(spec, localizedContext, languageTag) })
    }

    override fun getWeatherData(locationId: String, languageTag: String?): Flow<WeatherData> = flow {
        while (currentCoroutineContext().isActive) {
            emit(fetchWeatherData(locationId, languageTag))
            delay(15 * 60 * 1000L)
        }
    }

    override fun getCurrentTime(): LocalTime = lastCurrentTime ?: LocalTime.now()

    private suspend fun fetchWeatherData(locationId: String, languageTag: String?): WeatherData {
        val spec = locationSpecs.firstOrNull { it.id == locationId }
            ?: throw IllegalArgumentException("Unknown Google Weather location ID: $locationId")
        val localizedContext = context.localized(languageTag)
        val location = resolveLocation(spec, localizedContext, languageTag)

        val current = requestJson(
            buildWeatherUrl(
                path = "currentConditions:lookup",
                spec = spec,
                languageTag = languageTag
            )
        )
        val hourly = requestJson(
            buildWeatherUrl(
                path = "forecast/hours:lookup",
                spec = spec,
                languageTag = languageTag,
                params = mapOf("hours" to "24", "pageSize" to "24")
            )
        )
        val history = requestJson(
            buildWeatherUrl(
                path = "history/hours:lookup",
                spec = spec,
                languageTag = languageTag,
                params = mapOf("hours" to "6", "pageSize" to "6")
            )
        )
        val daily = requestJson(
            buildWeatherUrl(
                path = "forecast/days:lookup",
                spec = spec,
                languageTag = languageTag,
                params = mapOf("days" to "10", "pageSize" to "10")
            )
        )

        val zoneId = resolveZoneId(current, hourly, history, daily)
        val currentInstant = parseInstantOrNull(current.optStringOrNull("currentTime")) ?: Instant.now()
        val currentLocalDateTime = currentInstant.atZone(zoneId).toLocalDateTime()
        val currentLocalDate = currentLocalDateTime.toLocalDate()
        lastCurrentTime = currentLocalDateTime.toLocalTime()

        val currentTemperature = temperatureDegrees(current, "temperature").roundToInt()
        val currentFeelsLike = temperatureDegrees(current, "feelsLikeTemperature").roundToInt()
        val currentIsDay = current.optBoolean("isDaytime", true)
        val currentWeatherCondition = mapWeatherCondition(
            type = current.optJSONObject("weatherCondition")?.optStringOrNull("type"),
            isDay = currentIsDay
        )
        val currentCloudCover = current.optInt("cloudCover", 0)
        val currentWindSpeed = windValue(current, "speed").roundToInt()
        val currentWindGusts = windValue(current, "gust").roundToInt()
        val currentWindDirection = current.optJSONObject("wind")
            ?.optJSONObject("direction")
            ?.optDouble("degrees", 0.0)
            ?: 0.0
        val currentHumidity = current.optInt("relativeHumidity", 0)
        val currentDewPoint = temperatureDegrees(current, "dewPoint").roundToInt()
        val currentPressure = pressureMillibars(current).roundToInt()
        val currentUvIndex = current.optInt("uvIndex", 0)
        val currentPrecipitation = precipitationQuantity(current).toFloat()

        val hourlyForecast = buildHourlyForecast(
            historyHours = history.optJSONArray("historyHours").toObjectList(),
            forecastHours = hourly.optJSONArray("forecastHours").toObjectList(),
            zoneId = zoneId
        )
        val dailyForecast = buildDailyForecast(
            forecastDays = daily.optJSONArray("forecastDays").toObjectList(),
            currentDate = currentLocalDate
        )
        val todayForecast = dailyForecast.firstOrNull { !it.date.isBefore(currentLocalDate) }
            ?: dailyForecast.firstOrNull()
            ?: throw IOException("Google Weather daily forecast was empty")

        val referenceTemperature = referenceTemperatureFromHistory(
            current = current,
            currentTemperature = currentTemperature
        )
        val temperatureComparison = WeatherTextFormatter.temperatureComparison(
            context = localizedContext,
            currentTemperature = currentTemperature,
            referenceTemperature = referenceTemperature
        )
        val pressureTrend = WeatherTextFormatter.pressureTrend(
            context = localizedContext,
            diffHpa = buildPressureTrend(
                currentPressure = currentPressure.toDouble(),
                historyHours = history.optJSONArray("historyHours").toObjectList(),
                currentInstant = currentInstant
            )
        )

        val airQuality = runCatching { fetchAirQualityBundle(spec, languageTag) }
            .getOrDefault(AirQualityBundle.Unavailable)
        val pollen = runCatching { fetchPollenBundle(spec, languageTag) }
            .getOrDefault(PollenBundle.Unavailable)

        val weatherDetails = WeatherDetails(
            windSpeed = currentWindSpeed,
            windGusts = currentWindGusts,
            windDirection = WeatherTextFormatter.windDirection(localizedContext, currentWindDirection),
            humidity = currentHumidity,
            dewPoint = currentDewPoint,
            pressure = currentPressure,
            pressureTrend = pressureTrend,
            uvIndex = currentUvIndex,
            visibility = WeatherTextFormatter.visibility(localizedContext, visibilityMeters(current)),
            cloudCover = currentCloudCover,
            airQualityIndex = airQuality.currentAqi,
            pm25 = airQuality.currentPm25,
            pm10 = airQuality.currentPm10,
            no2 = airQuality.currentNo2,
            precipitation = currentPrecipitation,
            grassPollen = pollen.grassPollen,
            treePollen = pollen.treePollen,
            ragweedPollen = pollen.ragweedPollen,
            airQualityAvailable = airQuality.available,
            pollenAvailable = pollen.available
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
            location = location,
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
            sunInfo = buildSunInfo(
                forecastDays = daily.optJSONArray("forecastDays").toObjectList(),
                currentDate = currentLocalDate,
                zoneId = zoneId
            )
        )
    }

    private fun resolveLocation(spec: LocationSpec, localizedContext: Context, languageTag: String?): Location {
        val cacheKey = locationCacheKey(spec.id, languageTag)
        locationCache[cacheKey]?.let { return it }

        val location = Location(
            id = spec.id,
            name = localizedContext.locationNameForId(spec.id, spec.fallbackName),
            isCurrentLocation = spec.isCurrentLocation
        )
        locationCache[cacheKey] = location
        return location
    }

    private fun buildHourlyForecast(
        historyHours: List<JSONObject>,
        forecastHours: List<JSONObject>,
        zoneId: ZoneId
    ): List<HourlyForecast> {
        val historical = historyHours
            .sortedBy { parseStartInstant(it) ?: Instant.EPOCH }
            .takeLast(5)
            .map { hour -> parseHourlyForecast(hour, zoneId, isCurrent = false) }

        val forecast = forecastHours
            .take(24)
            .mapIndexed { index, hour -> parseHourlyForecast(hour, zoneId, isCurrent = index == 0) }

        return historical + forecast
    }

    private fun parseHourlyForecast(hour: JSONObject, zoneId: ZoneId, isCurrent: Boolean): HourlyForecast {
        val isDay = hour.optBoolean("isDaytime", true)
        return HourlyForecast(
            time = parseDisplayDateTime(hour, zoneId).toLocalTime(),
            temperature = temperatureDegrees(hour, "temperature").roundToInt(),
            conditionEnum = mapWeatherCondition(
                type = hour.optJSONObject("weatherCondition")?.optStringOrNull("type"),
                isDay = isDay
            ),
            precipitationChance = precipitationProbability(hour),
            isCurrent = isCurrent
        )
    }

    private fun buildDailyForecast(
        forecastDays: List<JSONObject>,
        currentDate: LocalDate
    ): List<DailyForecast> {
        return forecastDays.mapNotNull { day ->
            val date = parseDisplayDate(day) ?: return@mapNotNull null
            val daytime = day.optJSONObject("daytimeForecast") ?: day.optJSONObject("nighttimeForecast")
            DailyForecast(
                date = date,
                highTemp = temperatureDegrees(day, "maxTemperature").roundToInt(),
                lowTemp = temperatureDegrees(day, "minTemperature").roundToInt(),
                conditionEnum = mapWeatherCondition(
                    type = daytime?.optJSONObject("weatherCondition")?.optStringOrNull("type"),
                    isDay = true
                ),
                precipitationChance = daytime?.let { precipitationProbability(it) } ?: 0
            )
        }.filter { forecast ->
            !forecast.date.isBefore(currentDate)
        }
    }

    private fun buildSunInfo(
        forecastDays: List<JSONObject>,
        currentDate: LocalDate,
        zoneId: ZoneId
    ): SunInfo {
        val day = forecastDays.firstOrNull { parseDisplayDate(it) == currentDate }
            ?: forecastDays.firstOrNull()
        val sunEvents = day?.optJSONObject("sunEvents")

        return SunInfo(
            sunrise = parseInstantLocalTime(sunEvents?.optStringOrNull("sunriseTime"), zoneId)
                ?: LocalTime.of(6, 0),
            sunset = parseInstantLocalTime(sunEvents?.optStringOrNull("sunsetTime"), zoneId)
                ?: LocalTime.of(18, 0)
        )
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
            isSevereCondition(forecast.conditionEnum) || forecast.precipitationChance >= 80
        }
        val windAlert = currentWindSpeed >= 35 || currentWindGusts >= 50
        val uvAlert = currentUvIndex >= 7

        return when {
            severeHour != null && severeHour.precipitationChance >= 80 -> WeatherAlert(
                id = "google-weather-severe-rain",
                title = localizedContext.getString(R.string.weather_alert_warning_title),
                description = localizedContext.getString(R.string.weather_alert_intense_rain_description),
                severity = AlertSeverity.SEVERE,
                expirationTime = severeHour.time.plusHours(3).toString()
            )
            severeHour != null -> WeatherAlert(
                id = "google-weather-severe-weather",
                title = localizedContext.getString(R.string.weather_alert_warning_title),
                description = localizedContext.getString(R.string.weather_alert_rapid_change_description),
                severity = AlertSeverity.WARNING,
                expirationTime = severeHour.time.plusHours(3).toString()
            )
            windAlert -> WeatherAlert(
                id = "google-weather-wind",
                title = localizedContext.getString(R.string.weather_alert_wind_title),
                description = localizedContext.getString(R.string.weather_alert_wind_description),
                severity = AlertSeverity.WARNING,
                expirationTime = localizedContext.getString(R.string.weather_alert_expires_in_hours)
            )
            uvAlert -> WeatherAlert(
                id = "google-weather-uv",
                title = localizedContext.getString(R.string.weather_alert_uv_title),
                description = localizedContext.getString(R.string.weather_alert_uv_description),
                severity = AlertSeverity.INFO,
                expirationTime = localizedContext.getString(R.string.weather_alert_until_end_of_day)
            )
            isStormCondition(currentWeather) -> WeatherAlert(
                id = "google-weather-current-storm",
                title = localizedContext.getString(R.string.weather_alert_storm_title),
                description = localizedContext.getString(R.string.weather_alert_storm_description),
                severity = AlertSeverity.WARNING,
                expirationTime = localizedContext.getString(R.string.weather_alert_now)
            )
            else -> null
        }
    }

    private suspend fun fetchAirQualityBundle(spec: LocationSpec, languageTag: String?): AirQualityBundle {
        val body = JSONObject().apply {
            put("universalAqi", true)
            put(
                "location",
                JSONObject()
                    .put("latitude", spec.latitude)
                    .put("longitude", spec.longitude)
            )
            put(
                "extraComputations",
                JSONArray()
                    .put("LOCAL_AQI")
                    .put("POLLUTANT_CONCENTRATION")
            )
            put("languageCode", languageCode(languageTag))
        }

        val json = requestJson(
            url = "https://airquality.googleapis.com/v1/currentConditions:lookup?key=${encodeQuery(apiKey())}",
            method = "POST",
            requestBody = body.toString()
        )
        val indexes = json.optJSONArray("indexes").toObjectList()
        val selectedIndex = indexes.firstOrNull { it.optString("code").equals("usa_epa", ignoreCase = true) }
            ?: indexes.firstOrNull { it.optString("code").equals("uaqi", ignoreCase = true) }
            ?: indexes.firstOrNull()
        val pollutants = json.optJSONArray("pollutants").toObjectList()

        return AirQualityBundle(
            currentAqi = selectedIndex?.optInt("aqi", 0) ?: 0,
            currentPm25 = pollutantConcentration(pollutants, "pm25"),
            currentPm10 = pollutantConcentration(pollutants, "pm10"),
            currentNo2 = pollutantConcentration(pollutants, "no2"),
            available = selectedIndex != null || pollutants.isNotEmpty()
        )
    }

    private suspend fun fetchPollenBundle(spec: LocationSpec, languageTag: String?): PollenBundle {
        val url = buildString {
            append("https://pollen.googleapis.com/v1/forecast:lookup?")
            append("key=${encodeQuery(apiKey())}")
            append("&location.latitude=${spec.latitude}")
            append("&location.longitude=${spec.longitude}")
            append("&days=1")
            append("&pageSize=1")
            append("&plantsDescription=false")
            append("&languageCode=${encodeQuery(languageCode(languageTag))}")
        }
        val json = requestJson(url)
        val day = json.optJSONArray("dailyInfo").toObjectList().firstOrNull()
            ?: return PollenBundle.Unavailable
        val pollenTypes = day.optJSONArray("pollenTypeInfo").toObjectList()
        val plants = day.optJSONArray("plantInfo").toObjectList()

        return PollenBundle(
            grassPollen = pollenLevel(indexValue(pollenTypes.findCode("GRASS"))),
            treePollen = pollenLevel(indexValue(pollenTypes.findCode("TREE"))),
            ragweedPollen = pollenLevel(
                indexValue(plants.findCode("RAGWEED"))
                    ?: indexValue(pollenTypes.findCode("WEED"))
            ),
            available = pollenTypes.isNotEmpty() || plants.isNotEmpty()
        )
    }

    private suspend fun requestJson(
        url: String,
        method: String = "GET",
        requestBody: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Android-Package", context.packageName)
            androidCertificateSha1()?.let { certificateSha1 ->
                setRequestProperty("X-Android-Cert", certificateSha1)
            }
            if (requestBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            if (requestBody != null) {
                connection.outputStream.use { stream ->
                    stream.write(requestBody.toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("Google Weather request failed ($code): $body")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildWeatherUrl(
        path: String,
        spec: LocationSpec,
        languageTag: String?,
        params: Map<String, String> = emptyMap()
    ): String {
        return buildString {
            append("https://weather.googleapis.com/v1/")
            append(path)
            append("?key=${encodeQuery(apiKey())}")
            append("&location.latitude=${spec.latitude}")
            append("&location.longitude=${spec.longitude}")
            append("&unitsSystem=METRIC")
            append("&languageCode=${encodeQuery(languageCode(languageTag))}")
            params.forEach { (key, value) ->
                append("&${encodeQuery(key)}=${encodeQuery(value)}")
            }
        }
    }

    private fun apiKey(): String {
        return BuildConfig.GOOGLE_MAPS_API_KEY.takeIf { it.isNotBlank() }
            ?: throw IOException("Missing GOOGLE_MAPS_API_KEY in .env")
    }

    private fun androidCertificateSha1(): String? {
        return runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo
                    ?.apkContentsSigners
                    ?.firstOrNull()
                    ?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
                    ?.firstOrNull()
                    ?.toByteArray()
            } ?: return@runCatching null

            MessageDigest.getInstance("SHA-1")
                .digest(signatureBytes)
                .joinToString(separator = "") { byte ->
                    String.format(Locale.US, "%02X", byte.toInt() and 0xFF)
                }
        }.getOrNull()
    }

    private fun languageCode(languageTag: String?): String {
        return languageTag?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().toLanguageTag()
    }

    private fun temperatureDegrees(node: JSONObject, key: String): Double {
        return node.optJSONObject(key)?.optDouble("degrees", 0.0) ?: 0.0
    }

    private fun windValue(node: JSONObject, key: String): Double {
        return node.optJSONObject("wind")
            ?.optJSONObject(key)
            ?.optDouble("value", 0.0)
            ?: 0.0
    }

    private fun pressureMillibars(node: JSONObject): Double {
        return node.optJSONObject("airPressure")?.optDouble("meanSeaLevelMillibars", 0.0) ?: 0.0
    }

    private fun precipitationProbability(node: JSONObject): Int {
        return node.optJSONObject("precipitation")
            ?.optJSONObject("probability")
            ?.optInt("percent", 0)
            ?: 0
    }

    private fun precipitationQuantity(node: JSONObject): Double {
        return node.optJSONObject("precipitation")
            ?.optJSONObject("qpf")
            ?.optDouble("quantity", 0.0)
            ?: 0.0
    }

    private fun visibilityMeters(node: JSONObject): Int {
        val visibility = node.optJSONObject("visibility") ?: return 0
        val distance = visibility.optDouble("distance", 0.0)
        return when (visibility.optString("unit")) {
            "KILOMETERS" -> (distance * 1000.0).roundToInt()
            "MILES" -> (distance * 1609.34).roundToInt()
            else -> distance.roundToInt()
        }
    }

    private fun referenceTemperatureFromHistory(current: JSONObject, currentTemperature: Int): Double? {
        val change = current.optJSONObject("currentConditionsHistory")
            ?.optJSONObject("temperatureChange")
            ?.optDoubleOrNull("degrees")
            ?: return null
        return currentTemperature - change
    }

    private fun buildPressureTrend(
        currentPressure: Double,
        historyHours: List<JSONObject>,
        currentInstant: Instant
    ): Double? {
        if (historyHours.isEmpty()) return null
        val target = currentInstant.minus(Duration.ofHours(3))
        val closest = historyHours.minByOrNull { hour ->
            val start = parseStartInstant(hour) ?: target
            abs(Duration.between(start, target).toMinutes())
        }
        val pastPressure = closest?.let { pressureMillibars(it) }?.takeIf { it > 0.0 } ?: return null
        return currentPressure - pastPressure
    }

    private fun resolveZoneId(vararg nodes: JSONObject): ZoneId {
        val id = nodes.firstNotNullOfOrNull { node ->
            node.optJSONObject("timeZone")?.optStringOrNull("id")
        }
        return id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    }

    private fun parseDisplayDateTime(node: JSONObject, zoneId: ZoneId): LocalDateTime {
        val display = node.optJSONObject("displayDateTime")
        if (display != null) {
            val year = display.optInt("year", 1970)
            val month = display.optInt("month", 1)
            val day = display.optInt("day", 1)
            val hour = display.optInt("hours", display.optInt("hour", 0))
            val minute = display.optInt("minutes", display.optInt("minute", 0))
            val parsed = runCatching { LocalDateTime.of(year, month, day, hour, minute) }.getOrNull()
            if (parsed != null) return parsed
        }

        return parseStartInstant(node)?.atZone(zoneId)?.toLocalDateTime() ?: LocalDateTime.now(zoneId)
    }

    private fun parseDisplayDate(node: JSONObject): LocalDate? {
        val display = node.optJSONObject("displayDate") ?: node.optJSONObject("date") ?: return null
        val year = display.optInt("year", 0)
        val month = display.optInt("month", 0)
        val day = display.optInt("day", 0)
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun parseStartInstant(node: JSONObject): Instant? {
        return parseInstantOrNull(node.optJSONObject("interval")?.optStringOrNull("startTime"))
    }

    private fun parseInstantLocalTime(value: String?, zoneId: ZoneId): LocalTime? {
        return parseInstantOrNull(value)?.atZone(zoneId)?.toLocalTime()
    }

    private fun parseInstantOrNull(value: String?): Instant? {
        return value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }

    private fun mapWeatherCondition(type: String?, isDay: Boolean): WeatherCondition {
        val normalized = type.orEmpty().uppercase(Locale.US)
        return when (normalized) {
            "CLEAR", "MOSTLY_CLEAR" -> if (isDay) WeatherCondition.DAY_SUNNY else WeatherCondition.NIGHT_CLEAR
            "PARTLY_CLOUDY" -> if (isDay) WeatherCondition.DAY_PARTLY_CLOUDY else WeatherCondition.NIGHT_PARTLY_CLOUDY
            "MOSTLY_CLOUDY", "CLOUDY" -> if (isDay) WeatherCondition.DAY_CLOUDY else WeatherCondition.NIGHT_CLOUDY
            "WINDY" -> if (isDay) WeatherCondition.DAY_WIND else WeatherCondition.NIGHT_WIND
            "WIND_AND_RAIN" -> if (isDay) WeatherCondition.DAY_RAIN_MEDIUM else WeatherCondition.NIGHT_RAIN_MEDIUM
            "LIGHT_RAIN_SHOWERS",
            "CHANCE_OF_SHOWERS",
            "LIGHT_RAIN" -> if (isDay) WeatherCondition.DAY_RAIN_LIGHT else WeatherCondition.NIGHT_RAIN_LIGHT
            "SCATTERED_SHOWERS",
            "RAIN_SHOWERS",
            "LIGHT_TO_MODERATE_RAIN",
            "RAIN" -> if (isDay) WeatherCondition.DAY_RAIN_MEDIUM else WeatherCondition.NIGHT_RAIN_MEDIUM
            "HEAVY_RAIN_SHOWERS",
            "MODERATE_TO_HEAVY_RAIN",
            "HEAVY_RAIN",
            "RAIN_PERIODICALLY_HEAVY",
            "HAIL",
            "HAIL_SHOWERS" -> if (isDay) WeatherCondition.DAY_RAIN_HEAVY else WeatherCondition.NIGHT_RAIN_MEDIUM
            "LIGHT_SNOW_SHOWERS",
            "CHANCE_OF_SNOW_SHOWERS",
            "SCATTERED_SNOW_SHOWERS",
            "SNOW_SHOWERS",
            "HEAVY_SNOW_SHOWERS",
            "LIGHT_TO_MODERATE_SNOW",
            "MODERATE_TO_HEAVY_SNOW",
            "SNOW",
            "LIGHT_SNOW",
            "HEAVY_SNOW",
            "SNOWSTORM",
            "SNOW_PERIODICALLY_HEAVY",
            "HEAVY_SNOW_STORM",
            "BLOWING_SNOW",
            "RAIN_AND_SNOW" -> if (isDay) WeatherCondition.DAY_SNOW else WeatherCondition.NIGHT_SNOW
            "THUNDERSTORM",
            "THUNDERSHOWER",
            "SCATTERED_THUNDERSTORMS" -> if (isDay) WeatherCondition.DAY_THUNDERSTORM else WeatherCondition.NIGHT_THUNDERSTORM
            "LIGHT_THUNDERSTORM_RAIN" -> if (isDay) WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT else WeatherCondition.NIGHT_THUNDERSTORM_RAIN_LIGHT
            "HEAVY_THUNDERSTORM" -> if (isDay) WeatherCondition.DAY_THUNDERSTORM_HEAVY else WeatherCondition.NIGHT_THUNDERSTORM
            else -> when {
                "FOG" in normalized || "HAZE" in normalized || "DUST" in normalized || "SMOKE" in normalized ->
                    if (isDay) WeatherCondition.DAY_FOG else WeatherCondition.NIGHT_FOG
                "THUNDER" in normalized ->
                    if (isDay) WeatherCondition.DAY_THUNDERSTORM else WeatherCondition.NIGHT_THUNDERSTORM
                "SNOW" in normalized || "SLEET" in normalized ->
                    if (isDay) WeatherCondition.DAY_SNOW else WeatherCondition.NIGHT_SNOW
                "RAIN" in normalized || "SHOWER" in normalized || "DRIZZLE" in normalized ->
                    if (isDay) WeatherCondition.DAY_RAIN_LIGHT else WeatherCondition.NIGHT_RAIN_LIGHT
                else -> if (isDay) WeatherCondition.DAY_CLOUDY else WeatherCondition.NIGHT_CLOUDY
            }
        }
    }

    private fun isSevereCondition(condition: WeatherCondition): Boolean {
        return condition in setOf(
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
        )
    }

    private fun isStormCondition(condition: WeatherCondition): Boolean {
        return condition in setOf(
            WeatherCondition.DAY_THUNDERSTORM,
            WeatherCondition.DAY_THUNDERSTORM_HEAVY,
            WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT,
            WeatherCondition.DAY_THUNDERSTORM_RAIN_MEDIUM,
            WeatherCondition.NIGHT_THUNDERSTORM,
            WeatherCondition.NIGHT_THUNDERSTORM_RAIN_LIGHT,
            WeatherCondition.NIGHT_THUNDERSTORM_MEDIUM_RAIN
        )
    }

    private fun pollutantConcentration(pollutants: List<JSONObject>, code: String): Float {
        val pollutant = pollutants.firstOrNull { it.optString("code").equals(code, ignoreCase = true) }
            ?: return 0f
        val concentration = pollutant.optJSONObject("concentration") ?: return 0f
        val rawValue = concentration.optDouble("value", 0.0)
        val units = concentration.optString("units")
        val micrograms = when {
            code.equals("no2", ignoreCase = true) && units == "PARTS_PER_BILLION" -> rawValue * 1.88
            else -> rawValue
        }
        return micrograms.toFloat()
    }

    private fun List<JSONObject>.findCode(code: String): JSONObject? {
        return firstOrNull { it.optString("code").equals(code, ignoreCase = true) }
    }

    private fun indexValue(node: JSONObject?): Int? {
        return node?.optJSONObject("indexInfo")?.optInt("value", 0)
    }

    private fun pollenLevel(value: Int?): PollenLevel {
        return when (value ?: 0) {
            0 -> PollenLevel.NONE
            1, 2 -> PollenLevel.LOW
            3 -> PollenLevel.MEDIUM
            4 -> PollenLevel.HIGH
            else -> PollenLevel.VERY_HIGH
        }
    }

    private fun JSONArray?.toObjectList(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optJSONObject(index) }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        val value = optDouble(key, Double.NaN)
        return value.takeUnless { it.isNaN() }
    }

    private fun encodeQuery(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun locationCacheKey(locationId: String, languageTag: String?): String {
        return "${languageTag.orEmpty()}::$locationId"
    }
}
