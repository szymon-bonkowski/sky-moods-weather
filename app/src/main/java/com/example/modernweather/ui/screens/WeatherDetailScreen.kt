package com.example.modernweather.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modernweather.R
import com.example.modernweather.data.models.*
import com.example.modernweather.nowcast.model.LocalRiskLevel
import com.example.modernweather.nowcast.model.NowcastAssessment
import com.example.modernweather.ui.components.*
import com.example.modernweather.ui.theme.*
import com.example.modernweather.ui.viewmodel.*

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
                is WeatherDetailUiState.Success -> WeatherPage(
                    data = state.weatherData,
                    unit = settingsState.temperatureUnit,
                    viewModel = viewModel,
                    onNavigateToRadar = onNavigateToRadar
                )
            }
        }
    }
}

@Composable
fun WeatherPage(data: WeatherData, unit: TemperatureUnit, viewModel: WeatherViewModel, onNavigateToRadar: () -> Unit) {
    val stableSunInfo = remember(data.location.id) { data.sunInfo }
    val listState = rememberLazyListState()
    val isCurrentWeatherVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "current_weather" }
        }
    }
    val isListScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }
    val shouldRunHeavyEffects by remember {
        derivedStateOf { isCurrentWeatherVisible && !isListScrolling }
    }

    val stableHourlyForecast = remember(data.hourlyForecast) { data.hourlyForecast }
    val stableDailyForecast = remember(data.dailyForecast) { data.dailyForecast }
    val stableDetails = remember(data.weatherDetails) { data.weatherDetails }
    val stableCurrentWeather = remember(data.currentWeather) { data.currentWeather }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "current_weather", contentType = "current_weather") {
            CurrentWeatherSection(
                current = stableCurrentWeather,
                hourly = stableHourlyForecast,
                unit = unit,
                pauseEffects = !shouldRunHeavyEffects
            )
        }

        data.alert?.let { alert ->
            item(key = "alert_${alert.id}", contentType = "alert") {
                AlertCard(alert)
            }
        }


        item(key = "weekly_forecast", contentType = "weekly_chart") {
            TitledCard("PROGNOZA TYGODNIOWA") {
                WeeklyForecastChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    dailyForecasts = stableDailyForecast,
                    unit = unit
                )
            }
        }

        item(key = "details_aqi", contentType = "details") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailsGrid(details = stableDetails)
                AqiSection(details = stableDetails)
            }
        }

        item(key = "local_nowcast_status", contentType = "nowcast") {
            LocalNowcastCardItem(viewModel = viewModel)
        }

        item(key = "radar", contentType = "radar") {
            RadarCard(onClick = onNavigateToRadar)
        }

        item(key = "sun_cycle", contentType = "sun_cycle") {
            SunCycleSection(sunInfo = stableSunInfo, viewModel = viewModel)
        }
    }
}

@Composable
private fun LocalNowcastCardItem(viewModel: WeatherViewModel) {
    val nowcastAssessment by viewModel.nowcastAssessmentState.collectAsState()
    LocalNowcastCard(assessment = nowcastAssessment)
}

@Composable
fun LocalNowcastCard(assessment: NowcastAssessment) {
    val (title, color) = when (assessment.riskLevel) {
        LocalRiskLevel.LOW -> "Niskie ryzyko nagłej burzy/frontu" to Color(0xFF4CAF50)
        LocalRiskLevel.ELEVATED -> "Podwyższone ryzyko zmian pogodowych" to Color(0xFFFFB300)
        LocalRiskLevel.HIGH -> "Wysokie ryzyko gwałtownego załamania pogody" to Color(0xFFFF7043)
        LocalRiskLevel.SEVERE -> "Bardzo wysokie ryzyko burzy/silnego wiatru" to Color(0xFFE53935)
    }

    TitledCard("BAROMETR TELEFONU") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            assessment.latestPressureHpa?.let {
                Text(
                    text = "Ciśnienie: ${"%.1f".format(it)} hPa | Spadek 3h: ${"%.1f".format(assessment.pressureDrop3h)} hPa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = assessment.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
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

@Composable
fun DetailsGrid(details: WeatherDetails) {
    TitledCard(title = "SZCZEGÓŁY") {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Wiatr", "${details.windSpeed} km/h", Icons.Default.Air, Modifier.weight(1f))
                DetailItem("Ciśnienie", "${details.pressure} hPa", Icons.Default.Speed, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Wilgotność", "${details.humidity}%", Icons.Default.WaterDrop, Modifier.weight(1f))
                DetailItem("Indeks UV", "${details.uvIndex}", Icons.Default.WbSunny, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                DetailItem("Zachmurzenie", "${details.cloudCover}%", Icons.Default.Cloud, Modifier.weight(1f))
                DetailItem("Widoczność", details.visibility, Icons.Default.Visibility, Modifier.weight(1f))
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
    var isVisible by remember { mutableStateOf(false) }
    var animationPlayed by rememberSaveable(details.airQualityIndex) {
        mutableStateOf(false)
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    LaunchedEffect(isVisible) {
        if (isVisible && !animationPlayed) {
            animationPlayed = true
        }
    }

    TitledCard(title = "JAKOŚĆ POWIETRZA") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .onGloballyPositioned { coordinates ->
                    if (!animationPlayed) {
                        val yPosition = coordinates.positionInWindow().y
                        if (yPosition > 0 && yPosition < screenHeightPx) {
                            isVisible = true
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AqiGauge(
                    aqi = details.airQualityIndex,
                    size = 110.dp,
                    externalPlayed = animationPlayed
                )
            }

            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AqiComponentRow("PM2.5", details.pm25, 25f, animationPlayed)
                AqiComponentRow("PM10", details.pm10, 50f, animationPlayed)
                AqiComponentRow("NO₂", details.no2, 40f, animationPlayed)
            }
        }
    }
}

@Composable
fun AqiComponentRow(label: String, value: Float, maxValue: Float, animate: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${value.toInt()} µg/m³",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        
        val targetProgress = (value / (maxValue * 1.5f)).coerceIn(0.05f, 1f)
        val progress by animateFloatAsState(
            targetValue = if (animate) targetProgress else 0.05f,
            animationSpec = tween(durationMillis = 1500),
            label = "aqi_bar_$label"
        )

        val barColor = when {
            value <= maxValue * 0.5f -> AqiGood
            value <= maxValue -> AqiModerate
            value <= maxValue * 1.5f -> AqiUnhealthy
            else -> AqiVeryUnhealthy
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(barColor)
            )
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
fun SunCycleSection(sunInfo: SunInfo, viewModel: WeatherViewModel) {
    SunCycle(sunInfo = sunInfo, viewModel = viewModel)
}

@Composable
fun AlertCard(alert: WeatherAlert) {
    val isDark = isSystemInDarkTheme()

    val (containerColor, contentColor, icon) = when (alert.severity) {
        AlertSeverity.INFO -> Triple(
            if (isDark) Color(0xFF0F2942) else Color(0xFFE8F2FA),
            if (isDark) Color(0xFF7CB6F5) else Color(0xFF104A82),
            Icons.Default.Info
        )
        AlertSeverity.WARNING -> Triple(
            if (isDark) Color(0xFF3D2E1A) else Color(0xFFFDF3E1),
            if (isDark) Color(0xFFF3AF5D) else Color(0xFF8C500A),
            Icons.Default.Warning
        )
        AlertSeverity.SEVERE -> Triple(
            if (isDark) Color(0xFF451E1E) else Color(0xFFFCE8E8),
            if (isDark) Color(0xFFF28B8B) else Color(0xFF9E1B1B),
            Icons.Default.Dangerous
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp).padding(top = 2.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = alert.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Ważne do: ${alert.expirationTime}",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
        }
    }
}
