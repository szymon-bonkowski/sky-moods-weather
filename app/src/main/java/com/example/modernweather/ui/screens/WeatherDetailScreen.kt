package com.example.modernweather.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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
                    viewModel = viewModel,
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
    viewModel: WeatherViewModel,
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
                0 -> WeatherPage(data = data, unit = settings.temperatureUnit, viewModel = viewModel, onNavigateToRadar = onNavigateToRadar)
                1 -> AiInsightPage(insight = aiInsight)
            }
        }
    }
}

@Composable
fun WeatherPage(data: WeatherData, unit: TemperatureUnit, viewModel: WeatherViewModel, onNavigateToRadar: () -> Unit) {
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
                        .height(300.dp),
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
        item { SunCycleSection(sunInfo = data.sunInfo, viewModel = viewModel) }
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
        WeatherCondition.DAY_SUNNY, WeatherCondition.NIGHT_CLEAR -> Icons.Default.WbSunny
        WeatherCondition.DAY_PARTLY_CLOUDY, WeatherCondition.NIGHT_PARTLY_CLOUDY -> Icons.Default.WbCloudy
        WeatherCondition.DAY_CLOUDY, WeatherCondition.NIGHT_CLOUDY -> Icons.Default.Cloud
        WeatherCondition.DAY_RAIN_LIGHT, WeatherCondition.DAY_RAIN_MEDIUM, WeatherCondition.NIGHT_RAIN_LIGHT, WeatherCondition.NIGHT_RAIN_MEDIUM -> Icons.Outlined.WaterDrop
        WeatherCondition.DAY_RAIN_HEAVY -> Icons.Default.Grain
        WeatherCondition.DAY_THUNDERSTORM, WeatherCondition.NIGHT_THUNDERSTORM, WeatherCondition.DAY_THUNDERSTORM_HEAVY -> Icons.Default.Thunderstorm
        WeatherCondition.DAY_SNOW, WeatherCondition.NIGHT_SNOW -> Icons.Default.AcUnit
        WeatherCondition.DAY_FOG, WeatherCondition.NIGHT_FOG, WeatherCondition.DAY_FOG_CLOUDY -> Icons.Default.Dehaze
        WeatherCondition.DAY_WIND, WeatherCondition.NIGHT_WIND, WeatherCondition.DAY_WIND_CLOUDY -> Icons.Default.Air
        else -> Icons.Default.Cloud
    }
}

@Composable
fun DetailsGrid(details: WeatherDetails) {
    TitledCard(title = "SZCZEGÓŁY") {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .height(150.dp),
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
fun SunCycleSection(sunInfo: SunInfo, viewModel: WeatherViewModel) {
    val now = viewModel.getCurrentTime()
    val sunrise = sunInfo.sunrise
    val sunset = sunInfo.sunset

    val totalDaylightDuration = Duration.between(sunrise, sunset)
    val totalNightDuration = Duration.ofHours(24).minus(totalDaylightDuration)

    val isDay = !now.isBefore(sunrise) && !now.isAfter(sunset)

    val progress = if (isDay) {
        val elapsedDaytime = Duration.between(sunrise, now)
        (elapsedDaytime.toMinutes().toFloat() / totalDaylightDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
    } else {
        val elapsedNighttime = if (now.isAfter(sunset)) {
            Duration.between(sunset, now)
        } else {
            val timeUntilMidnight = Duration.between(sunset, LocalTime.MAX)
            val timeFromMidnight = Duration.between(LocalTime.MIN, now)
            timeUntilMidnight.plus(timeFromMidnight)
        }
        (elapsedNighttime.toMinutes().toFloat() / totalNightDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
    }

    val daylightHours = totalDaylightDuration.toHours()
    val daylightMinutes = totalDaylightDuration.toMinutes() % 60

    TitledCard(title = "WSCHÓD I ZACHÓD SŁOŃCA") {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SunArc(progress = progress, isDay = isDay)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Wschód", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        sunrise.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "${daylightHours}h ${daylightMinutes}m światła dziennego",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Zachód", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        sunset.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class StyledCloud(
    val initialRelativeOffset: Offset,
    val relativeSize: Size,
    val isForeground: Boolean
)

private fun DrawScope.drawStyledCloud(cloud: StyledCloud, globalAlpha: Float) {
    val drawingAreaWidth = size.width * 2
    val drawingAreaHeight = size.height

    val cloudAlpha = if (cloud.isForeground) 0.9f else 0.6f
    val finalAlpha = cloudAlpha * globalAlpha

    val baseColor = Color.White.copy(alpha = finalAlpha)
    val shadowColor = Color(0x99D0D0D8).copy(alpha = finalAlpha * 0.35f)

    val cloudSize = Size(
        width = cloud.relativeSize.width * drawingAreaWidth / 2,
        height = cloud.relativeSize.height * drawingAreaHeight
    )
    val topLeft = Offset(
        x = cloud.initialRelativeOffset.x * drawingAreaWidth / 2,
        y = cloud.initialRelativeOffset.y * drawingAreaHeight
    )
    val cornerRadiusValue = cloudSize.height / 2f

    drawRoundRect(
        color = shadowColor,
        topLeft = topLeft.copy(y = topLeft.y + cloudSize.height * 0.1f),
        size = cloudSize,
        cornerRadius = CornerRadius(cornerRadiusValue)
    )

    drawRoundRect(
        color = baseColor,
        topLeft = topLeft,
        size = cloudSize,
        cornerRadius = CornerRadius(cornerRadiusValue)
    )
}

@Composable
fun SunArc(progress: Float, isDay: Boolean) {
    val horizonColor = Color(0xFFFFD700)
    val middayColor = Color(0xFFFF6F00)
    val arcTrackColor = MaterialTheme.colorScheme.surfaceVariant
    val skyColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    val nightSkyColor = Color(0xFF001B3A).copy(alpha = 0.2f)

    val moonColor = Color(0xFFE0E0E0)
    val moonPathStartColor = Color.White
    val moonPathEndColor = Color(0xFFB0B0B0)

    val targetAngle = if (isDay) 180f + 180f * progress else 360f - 180f * progress

    val animatedAngle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = tween(durationMillis = 1500),
        label = "animated_angle"
    )

    val sunPathProgress by animateFloatAsState(
        targetValue = if (isDay) progress else 0f,
        animationSpec = if (isDay) tween(durationMillis = 500) else tween(durationMillis = 1500),
        label = "sun_path_progress"
    )

    val moonPathProgress by animateFloatAsState(
        targetValue = if (!isDay) progress else 0f,
        animationSpec = if (!isDay) tween(durationMillis = 500) else tween(durationMillis = 1500),
        label = "moon_path_progress"
    )

    val alphaIsDay = remember { mutableStateOf(isDay) }
    LaunchedEffect(isDay) {
        if (isDay != alphaIsDay.value) {
            delay(1500L)
            alphaIsDay.value = isDay
        }
    }

    val sunAlpha by animateFloatAsState(
        targetValue = if (alphaIsDay.value) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "sun_alpha_anim"
    )
    val moonAlpha by animateFloatAsState(
        targetValue = if (!alphaIsDay.value) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "moon_alpha_anim"
    )

    val starData = remember {
        List(100) {
            StarData(
                position = Offset(Random.nextFloat(), Random.nextFloat()),
                baseAlpha = Random.nextFloat() * 0.8f + 0.2f,
                baseRadius = Random.nextFloat() * 1.5f + 0.5f,
                twinkleSpeed = Random.nextFloat() * 2f + 1f,
                phaseOffset = Random.nextFloat() * 2f * Math.PI.toFloat()
            )
        }
    }

    val clouds = remember {
        listOf(
            StyledCloud(Offset(0.75f, 0.28f), Size(0.24f, 0.10f), isForeground = false),
            StyledCloud(Offset(0.15f, 0.33f), Size(0.28f, 0.12f), isForeground = false),

            StyledCloud(Offset(0.5f, 0.38f), Size(0.32f, 0.14f), isForeground = true),
            StyledCloud(Offset(0.0f, 0.46f), Size(0.20f, 0.10f), isForeground = true)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "animations")

    var continuousTime by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameTime ->
                continuousTime = (frameTime - startTime) / 1_000_000_000f
            }
        }
    }

    val sunGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sun_glow_pulse"
    )

    val moonGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "moon_glow_pulse"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val arcRadius = kotlin.math.min(canvasWidth / 2f, canvasHeight * 0.95f)
        val arcCenter = Offset(x = canvasWidth / 2f, y = canvasHeight)
        val arcSize = Size(width = arcRadius * 2, height = arcRadius * 2)
        val arcTopLeft = Offset(arcCenter.x - arcRadius, arcCenter.y - arcRadius)
        val horizonY = canvasHeight

        val skyPath = Path().apply {
            moveTo(arcCenter.x - arcRadius, horizonY)
            arcTo(rect = Rect(arcTopLeft, arcSize), startAngleDegrees = 180f, sweepAngleDegrees = 180f, forceMoveTo = false)
            close()
        }
        drawLine(color = arcTrackColor, start = Offset(0f, horizonY), end = Offset(canvasWidth, horizonY), strokeWidth = 3f)
        val currentSkyColor = lerp(nightSkyColor, skyColor, sunAlpha)
        drawPath(path = skyPath, brush = Brush.verticalGradient(listOf(currentSkyColor, Color.Transparent), startY = arcCenter.y - arcRadius, endY = horizonY))

        if (sunAlpha > 0f) {
            val cloudscapeWidth = size.width
            val period = 120f

            val backgroundSpeed = (cloudscapeWidth * 0.7f) / period
            val totalBackgroundDrift = continuousTime * backgroundSpeed
            val backgroundOffset = (totalBackgroundDrift % cloudscapeWidth + cloudscapeWidth) % cloudscapeWidth
            val backgroundDrift = -backgroundOffset

            val foregroundSpeed = cloudscapeWidth / period
            val totalForegroundDrift = continuousTime * foregroundSpeed
            val foregroundOffset = (totalForegroundDrift % cloudscapeWidth + cloudscapeWidth) % cloudscapeWidth
            val foregroundDrift = -foregroundOffset

            clipPath(path = skyPath) {
                translate(left = backgroundDrift - cloudscapeWidth) {
                    clouds.filter { !it.isForeground }.forEach { cloud -> drawStyledCloud(cloud, sunAlpha) }
                }
                translate(left = backgroundDrift) {
                    clouds.filter { !it.isForeground }.forEach { cloud -> drawStyledCloud(cloud, sunAlpha) }
                }
                translate(left = backgroundDrift + cloudscapeWidth) {
                    clouds.filter { !it.isForeground }.forEach { cloud -> drawStyledCloud(cloud, sunAlpha) }
                }

                translate(left = foregroundDrift - cloudscapeWidth) {
                    clouds.filter { it.isForeground }.forEach { cloud -> drawStyledCloud(cloud, sunAlpha) }
                }
                translate(left = foregroundDrift) {
                    clouds.filter { it.isForeground }.forEach { cloud -> drawStyledCloud(cloud, sunAlpha) }
                }
                translate(left = foregroundDrift + cloudscapeWidth) {
                    clouds.filter { it.isForeground }.forEach { cloud -> drawStyledCloud(cloud, sunAlpha) }
                }
            }
        }

        drawArc(color = arcTrackColor, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(width = 6f))

        if (moonAlpha > 0) {
            starData.forEach { star ->
                val starX = arcCenter.x + (star.position.x - 0.5f) * 2 * arcRadius * 0.95f
                val starY = horizonY - star.position.y * arcRadius * 0.9f
                val twinklePhase = continuousTime * star.twinkleSpeed + star.phaseOffset
                val twinkleIntensity = (sin(twinklePhase) + 1f) / 2f
                val currentStarAlpha = (star.baseAlpha * (0.4f + 0.6f * twinkleIntensity)) * moonAlpha
                val currentStarRadius = star.baseRadius * (0.8f + 0.2f * twinkleIntensity)
                val distance = kotlin.math.sqrt((starX - arcCenter.x).let { it * it } + (starY - arcCenter.y).let { it * it })
                if (distance <= arcRadius - (currentStarRadius + 8f) && starY < horizonY - (currentStarRadius + 2f)) {
                    drawCircle(color = Color.White.copy(alpha = currentStarAlpha), radius = currentStarRadius, center = Offset(starX, starY))
                }
            }
        }

        if (sunPathProgress > 0f) {
            val sunStart = 180f + 180f * (1f - sunPathProgress).let { if (isDay) 0f else it }
            val sunSweep = 180f * sunPathProgress
            drawArc(brush = Brush.linearGradient(0f to horizonColor, 0.5f to middayColor, 1f to horizonColor), startAngle = sunStart, sweepAngle = sunSweep, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(width = 6f))
        }
        if (moonPathProgress > 0f) {
            val moonStart = 360f - 180f * (1f - moonPathProgress).let { if (!isDay) 0f else it }
            val moonSweep = -180f * moonPathProgress
            drawArc(brush = Brush.linearGradient(0f to moonPathEndColor, 1f to moonPathStartColor), startAngle = moonStart, sweepAngle = moonSweep, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(width = 6f))
        }

        val angleRad = Math.toRadians(animatedAngle.toDouble()).toFloat()
        val objectPosition = Offset(arcCenter.x + cos(angleRad) * arcRadius, arcCenter.y + sin(angleRad) * arcRadius)

        if (sunAlpha > 0f) {
            val sunColorFactor = 1f - kotlin.math.abs(progress - 0.5f) * 2f
            val enhancedSunColorFactor = 1f - (sunColorFactor * sunColorFactor)
            val richMiddayColor = lerp(middayColor, horizonColor, 0.3f)
            val currentSunColor = lerp(horizonColor, richMiddayColor, enhancedSunColorFactor)
            val currentSunGlowAlpha = sunGlowPulse * sunAlpha
            val sunGlow = currentSunColor.copy(alpha = currentSunGlowAlpha)
            drawCircle(brush = Brush.radialGradient(listOf(sunGlow, Color.Transparent), center = objectPosition, radius = 22.dp.toPx()), radius = 22.dp.toPx(), center = objectPosition)
            drawCircle(color = currentSunColor.copy(alpha = sunAlpha), radius = 11.dp.toPx(), center = objectPosition)
        }
        if (moonAlpha > 0f) {
            val currentMoonGlowAlpha = moonGlowPulse * moonAlpha
            val moonGlow = Color.White.copy(alpha = currentMoonGlowAlpha)
            drawCircle(brush = Brush.radialGradient(listOf(moonGlow, Color.Transparent), center = objectPosition, radius = 20.dp.toPx()), radius = 20.dp.toPx(), center = objectPosition)
            drawCircle(color = moonColor.copy(alpha = moonAlpha), radius = 11.dp.toPx(), center = objectPosition)
        }
    }
}


data class StarData(
    val position: Offset,
    val baseAlpha: Float,
    val baseRadius: Float,
    val twinkleSpeed: Float,
    val phaseOffset: Float
)

@Composable
fun AlertCard(alert: WeatherAlert) {
    val (backgroundColor, contentColor, icon) = when (alert.severity) {
        AlertSeverity.INFO -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.Info
        )
        AlertSeverity.WARNING -> Triple(
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
            Icons.Default.Warning
        )
        AlertSeverity.SEVERE -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFD32F2F),
            Icons.Default.Dangerous
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alert.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ważne do: ${alert.expirationTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}