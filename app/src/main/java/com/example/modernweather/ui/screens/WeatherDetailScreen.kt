package com.example.modernweather.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modernweather.data.models.*
import com.example.modernweather.ui.components.shimmerBackground
import com.example.modernweather.ui.theme.*
import com.example.modernweather.ui.viewmodel.WeatherDetailUiState
import com.example.modernweather.ui.viewmodel.WeatherViewModel
import com.example.modernweather.utils.WeatherIconMapper
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import kotlin.math.roundToInt

fun toFahrenheit(celsius: Int): Int {
    return (celsius * 9 / 5) + 32
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailScreen(
    locationId: String,
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(locationId) {
        viewModel.loadWeatherData(locationId)
    }

    val uiState by viewModel.weatherDetailState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = uiState is WeatherDetailUiState.Success,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        (uiState as? WeatherDetailUiState.Success)?.weatherData?.location?.let {
                            Text(it.name)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val state = uiState) {
                is WeatherDetailUiState.Loading -> WeatherLoadingSceleton()
                is WeatherDetailUiState.Error -> ErrorState(message = state.message)
                is WeatherDetailUiState.Success -> WeatherContent(
                    data = state.weatherData,
                    settings = settingsState.userSettings
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherContent(data: WeatherData, settings: UserSettings) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            CurrentWeatherSection(data.currentWeather, settings.temperatureUnit)
        }
        data.alert?.let {
            item { AlertCard(it) }
        }
        item {
            HourlyForecastSection(data.hourlyForecast, settings.temperatureUnit)
        }
        item {
            DailyForecastSection(data.dailyForecast, settings.temperatureUnit)
        }
        item {
            WeatherRadarPreview()
        }
        item {
            DetailsGrid(data.weatherDetails, settings.temperatureUnit)
        }
        item {
            SunCycleSection(data.sunInfo)
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CurrentWeatherSection(current: CurrentWeather, unit: TemperatureUnit) {
    val displayTemp = if (unit == TemperatureUnit.CELSIUS) current.temperature else toFahrenheit(current.temperature)
    val displayFeelsLike = if (unit == TemperatureUnit.CELSIUS) current.feelsLike else toFahrenheit(current.feelsLike)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedContent(
            targetState = displayTemp,
            transitionSpec = {
                (slideInVertically { height -> height } + fadeIn()) togetherWith
                        (slideOutVertically { height -> -height } + fadeOut())
            }, label = "temp_anim"
        ) { temp ->
            Text(
                text = "$temp°",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 100.sp,
                    fontWeight = FontWeight.Light
                )
            )
        }

        Text(
            text = current.condition,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Odczuwalna $displayFeelsLike°",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HourlyForecastSection(hourly: List<HourlyForecast>, unit: TemperatureUnit) {
    TitledCard(title = "PROGNOZA GODZINOWA") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(hourly) { forecast ->
                HourlyItem(forecast, unit)
            }
        }
    }
}

@Composable
fun HourlyItem(forecast: HourlyForecast, unit: TemperatureUnit) {
    val displayTemp = if (unit == TemperatureUnit.CELSIUS) forecast.temperature else toFahrenheit(forecast.temperature)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            forecast.time.format(DateTimeFormatter.ofPattern("HH:mm")),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = WeatherIconMapper.getIcon(forecast.conditionEnum),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text("$displayTemp°", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (forecast.precipitationChance > 10) {
            Text(
                "${forecast.precipitationChance}%",
                fontSize = 12.sp,
                color = BlueSky,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun DailyForecastSection(daily: List<DailyForecast>, unit: TemperatureUnit) {
    TitledCard(title = "PROGNOZA 7-DNIOWA") {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            daily.forEach { forecast ->
                DailyItem(forecast, unit)
                Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
fun DailyItem(forecast: DailyForecast, unit: TemperatureUnit) {
    val displayHigh = if (unit == TemperatureUnit.CELSIUS) forecast.highTemp else toFahrenheit(forecast.highTemp)
    val displayLow = if (unit == TemperatureUnit.CELSIUS) forecast.lowTemp else toFahrenheit(forecast.lowTemp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = forecast.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pl")).replaceFirstChar { it.titlecase(Locale.forLanguageTag("pl")) },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Icon(
            imageVector = WeatherIconMapper.getIcon(forecast.conditionEnum),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).size(28.dp)
        )
        Text(
            text = "$displayHigh°",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "$displayLow°",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun WeatherRadarPreview() {
    TitledCard(title = "RADAR POGODOWY") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(RadarGradientEnd, RadarGradientStart),
                        radius = 300f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Radar,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(64.dp)
            )
            Text("Podgląd radaru (funkcja wkrótce)", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
    }
}


@Composable
fun DetailsGrid(details: WeatherDetails, unit: TemperatureUnit) {
    TitledCard(title = "SZCZEGÓŁY POGODOWE") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Wiatr", "${details.windSpeed} km/h", Icons.Default.Air, Modifier.weight(1f))
                DetailItem("Porywy", "${details.windGusts} km/h", Icons.Default.Cyclone, Modifier.weight(1f))
            }
             Row(Modifier.fillMaxWidth()) {
                DetailItem("Ciśnienie", "${details.pressure} hPa", Icons.Default.Speed, Modifier.weight(1f))
                DetailItem("Wilgotność", "${details.humidity}%", Icons.Default.WaterDrop, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Indeks UV", "${details.uvIndex}", Icons.Default.WbSunny, Modifier.weight(1f))
                DetailItem("Widoczność", details.visibility, Icons.Default.Visibility, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SunCycleSection(sunInfo: SunInfo) {
    val now = LocalTime.now()
    val totalDaylightMinutes = Duration.between(sunInfo.sunrise, sunInfo.sunset).toMinutes().toFloat()
    val elapsedMinutes = if (now.isAfter(sunInfo.sunrise) && now.isBefore(sunInfo.sunset)) {
        Duration.between(sunInfo.sunrise, now).toMinutes().toFloat()
    } else if (now.isAfter(sunInfo.sunset)) {
        totalDaylightMinutes
    } else {
        0f
    }

    val progress = (elapsedMinutes / totalDaylightMinutes).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(1500), label = "sun_progress")

    TitledCard(title = "WSCHÓD I ZACHÓD SŁOŃCA") {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SunArc(progress = animatedProgress)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Wschód: ${sunInfo.sunrise.format(DateTimeFormatter.ofPattern("HH:mm"))}")
                Text("Zachód: ${sunInfo.sunset.format(DateTimeFormatter.ofPattern("HH:mm"))}")
            }
        }
    }
}


@Composable
fun WeatherLoadingSceleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Box(modifier = Modifier.fillMaxWidth(0.6f).height(100.dp).align(Alignment.CenterHorizontally).shimmerBackground(RoundedCornerShape(16.dp)))
        Box(modifier = Modifier.fillMaxWidth(0.4f).height(30.dp).align(Alignment.CenterHorizontally).shimmerBackground(RoundedCornerShape(16.dp)))
        Spacer(modifier = Modifier.height(20.dp))
        Box(modifier = Modifier.fillMaxWidth().height(150.dp).shimmerBackground(MaterialTheme.shapes.large))
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).shimmerBackground(MaterialTheme.shapes.large))
    }
}

@Composable
fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TitledCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large
        ) {
            content()
        }
    }
}

@Composable
fun AlertCard(alert: WeatherAlert) {
    val backgroundColor = when (alert.severity) {
        AlertSeverity.INFO -> Primary.copy(alpha = 0.3f)
        AlertSeverity.WARNING -> AlertWarning.copy(alpha = 0.8f)
        AlertSeverity.SEVERE -> AlertSevere
    }
    val contentColor = if (alert.severity == AlertSeverity.SEVERE) Color.White else MaterialTheme.colorScheme.onSurface

    TitledCard(title = alert.title.uppercase()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(alert.description, fontSize = 14.sp, color = contentColor)
            Text(alert.expirationTime, fontSize = 12.sp, color = contentColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun SunArc(progress: Float) {
    val stroke = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f))
    val sunColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)) {
        val arcSize = Size(width = size.width * 0.7f, height = size.width * 0.7f)
        val arcTopLeft = Offset(x = (size.width - arcSize.width) / 2, y = size.height * 0.3f)

        drawArc(
            color = OnSurfaceVariant.copy(alpha = 0.5f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke
        )

        val angle = (180 * progress) + 180
        val angleRad = Math.toRadians(angle.toDouble()).toFloat()
        val radius = arcSize.width / 2
        val sunX = center.x + radius * kotlin.math.cos(angleRad)
        val sunY = (arcTopLeft.y + radius) + radius * kotlin.math.sin(angleRad)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(sunColor.copy(alpha = 0.3f), Color.Transparent)
            ),
            radius = 24f,
            center = Offset(sunX, sunY)
        )
        drawCircle(
            color = sunColor,
            radius = 12f,
            center = Offset(sunX, sunY)
        )
    }
}
