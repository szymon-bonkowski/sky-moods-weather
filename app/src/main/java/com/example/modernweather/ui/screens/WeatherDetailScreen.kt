package com.example.modernweather.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modernweather.R
import com.example.modernweather.data.models.*
import com.example.modernweather.ui.components.*
import com.example.modernweather.ui.theme.*
import com.example.modernweather.ui.viewmodel.SettingsUiState
import com.example.modernweather.ui.viewmodel.WeatherDetailUiState
import com.example.modernweather.ui.viewmodel.WeatherViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

fun toFahrenheit(celsius: Int): Int {
    return (celsius * 9 / 5) + 32
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailScreen(
    locationId: String,
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRadar: () -> Unit
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
                    val locationName = (uiState as? WeatherDetailUiState.Success)?.weatherData?.location?.name ?: ""
                    AnimatedContent(targetState = locationName, label = "title_anim") { name ->
                        if (name.isNotEmpty()) {
                            Text(name)
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
                    settings = settingsState,
                    aiInsight = state.aiInsight,
                    onNavigateToRadar = onNavigateToRadar
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherContent(
    data: WeatherData,
    settings: SettingsUiState,
    aiInsight: String,
    onNavigateToRadar: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            Tab(selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Pogoda") })
            Tab(selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("AI Insights") })
        }

        HorizontalPager(state = pagerState) { page ->
            when (page) {
                0 -> WeatherPage(data = data, unit = settings.temperatureUnit, onNavigateToRadar = onNavigateToRadar)
                1 -> AiInsightPage(insight = aiInsight)
            }
        }
    }
}

@Composable
fun WeatherPage(data: WeatherData, unit: TemperatureUnit, onNavigateToRadar: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { CurrentWeatherSection(current = data.currentWeather, unit = unit) }
        data.alert?.let { item { AlertCard(it) } }
        item { HourlyForecastSection(hourly = data.hourlyForecast, unit = unit) }

        item {
            TitledCard("PROGNOZA TYGODNIOWA") {
                WeeklyForecastChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    dailyForecasts = data.dailyForecast,
                    unit = unit
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    DetailsGrid(details = data.weatherDetails)
                }
                Box(modifier = Modifier.weight(1f)) {
                    AqiSection(details = data.weatherDetails)
                }
            }
        }
        item { RadarCard(onClick = onNavigateToRadar) }
        item { SunCycleSection(sunInfo = data.sunInfo) }
    }
}

@Composable
fun AiInsightPage(insight: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "AI Icon",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Analiza AI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = insight,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(100.dp)
                .align(Alignment.CenterHorizontally)
                .shimmerBackground(RoundedCornerShape(16.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(30.dp)
                .align(Alignment.CenterHorizontally)
                .shimmerBackground(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .shimmerBackground(MaterialTheme.shapes.large)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .shimmerBackground(MaterialTheme.shapes.large)
        )
    }
}

@Composable
fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
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
            imageVector = mapConditionToIconFromHourly(forecast.conditionEnum),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text("$displayTemp°", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (forecast.precipitationChance > 10) {
            Text(
                "${forecast.precipitationChance}%",
                fontSize = 12.sp,
                color = AccentBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun mapConditionToIconFromHourly(condition: WeatherCondition): ImageVector {
    return when (condition) {
        WeatherCondition.SUNNY -> Icons.Default.WbSunny
        WeatherCondition.PARTLY_CLOUDY -> Icons.Default.WbCloudy
        WeatherCondition.CLOUDY -> Icons.Default.Cloud
        WeatherCondition.RAIN -> Icons.Outlined.WaterDrop
        WeatherCondition.HEAVY_RAIN -> Icons.Default.Grain
        WeatherCondition.THUNDERSTORM -> Icons.Default.Thunderstorm
        WeatherCondition.SNOW -> Icons.Default.AcUnit
        WeatherCondition.FOG -> Icons.Default.Dehaze
    }
}

@Composable
fun DetailsGrid(details: WeatherDetails) {
    TitledCard(title = "SZCZEGÓŁY") {
        Column(
            modifier = Modifier.padding(16.dp).height(150.dp),
            verticalArrangement = Arrangement.SpaceAround
        ) {
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Wiatr", "${details.windSpeed} km/h", Icons.Default.Air, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Ciśnienie", "${details.pressure} hPa", Icons.Default.Speed, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Wilgotność", "${details.humidity}%", Icons.Default.WaterDrop, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
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
fun AqiSection(details: WeatherDetails) {
    TitledCard(title = "JAKOŚĆ POWIETRZA") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AqiGauge(aqi = details.airQualityIndex)
        }
    }
}

@Composable
fun RadarCard(onClick: () -> Unit) {
    TitledCard(title = "RADAR") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clickable(onClick = onClick)
                .padding(8.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            Image(
                painter = painterResource(id = R.drawable.map_background),
                contentDescription = "Radar preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = Color.White)
                    Text("Otwórz mapę radaru", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
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
fun SunArc(progress: Float) {
    val sunColor = MaterialTheme.colorScheme.primary
    val arcColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)) {
        val stroke = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f))

        val arcSize = Size(width = size.width * 0.7f, height = size.width * 0.7f)
        val arcTopLeft = Offset(x = (size.width - arcSize.width) / 2f, y = size.height * 0.3f)

        drawArc(
            color = arcColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke
        )

        val angle = (180 * progress) + 180
        val angleRad = Math.toRadians(angle.toDouble()).toFloat()
        val radius = arcSize.width / 2f
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

@Composable
fun AlertCard(alert: WeatherAlert) {
    val backgroundColor = when (alert.severity) {
        AlertSeverity.INFO -> Primary.copy(alpha = 0.3f)
        AlertSeverity.WARNING -> AlertWarning
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