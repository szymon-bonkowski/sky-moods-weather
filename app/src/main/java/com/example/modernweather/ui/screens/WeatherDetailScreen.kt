package com.example.modernweather.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modernweather.data.models.*
import com.example.modernweather.ui.theme.*
import com.example.modernweather.ui.viewmodel.WeatherDetailUiState
import com.example.modernweather.ui.viewmodel.WeatherViewModel
import com.example.modernweather.utils.WeatherIconMapper
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState is WeatherDetailUiState.Success) {
                        Text((uiState as WeatherDetailUiState.Success).weatherData.location.name)
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
                is WeatherDetailUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is WeatherDetailUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is WeatherDetailUiState.Success -> {
                    WeatherContent(state.weatherData)
                }
            }
        }
    }
}

@Composable
fun WeatherContent(data: WeatherData) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { CurrentWeatherSection(data.currentWeather) }
        data.alert?.let { item { AlertCard(it) } }
        item { HourlyForecastSection(data.hourlyForecast) }
        item { DailyForecastSection(data.dailyForecast) }
        item { WeatherRadarPreview() }
        item { DetailsGrid(data.weatherDetails) }
        item { SunCycleSection(data.sunInfo) }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun CurrentWeatherSection(current: CurrentWeather) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${current.temperature}°",
            fontSize = 96.sp,
            fontWeight = FontWeight.Thin
        )
        Text(
            text = current.condition,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Odczuwalna ${current.feelsLike}°",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Max: ${current.highTemp}°   Min: ${current.lowTemp}°",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(alert.title, fontWeight = FontWeight.Bold, color = contentColor)
            Spacer(Modifier.height(4.dp))
            Text(alert.description, fontSize = 14.sp, color = contentColor)
            Spacer(Modifier.height(8.dp))
            Text(alert.expirationTime, fontSize = 12.sp, color = contentColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun HourlyForecastSection(hourly: List<HourlyForecast>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("PROGNOZA GODZINOWA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                items(hourly) { forecast ->
                    HourlyItem(forecast)
                }
            }
        }
    }
}

@Composable
fun HourlyItem(forecast: HourlyForecast) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            forecast.time.format(DateTimeFormatter.ofPattern("HH:mm")),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Icon(
            imageVector = WeatherIconMapper.getIcon(forecast.conditionEnum),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text("${forecast.temperature}°", fontWeight = FontWeight.Bold)
        if (forecast.precipitationChance > 10) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${forecast.precipitationChance}%",
                fontSize = 12.sp,
                color = BlueSky
            )
        }
    }
}

@Composable
fun DailyForecastSection(daily: List<DailyForecast>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("PROGNOZA 7-DNIOWA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            daily.forEach { forecast ->
                DailyItem(forecast)
            }
        }
    }
}

@Composable
fun DailyItem(forecast: DailyForecast) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = forecast.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pl")).capitalize(Locale.ROOT),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = WeatherIconMapper.getIcon(forecast.conditionEnum),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (forecast.precipitationChance > 10) {
            Text(
                text = "${forecast.precipitationChance}%",
                color = BlueSky,
                fontSize = 14.sp,
                modifier = Modifier.width(40.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        Text(
            text = "${forecast.highTemp}°",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    )
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${forecast.lowTemp}°",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun WeatherRadarPreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("Podgląd radaru pogodowego", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DetailsGrid(details: WeatherDetails) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SZCZEGÓŁY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem("Wiatr", "${details.windSpeed} ${details.windDirection}")
                DetailItem("Wilgotność", "${details.humidity}%")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem("Ciśnienie", "${details.pressure} hPa")
                DetailItem("Punkt rosy", "${details.dewPoint}°")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailItem("Indeks UV", "${details.uvIndex}")
                DetailItem("Widoczność", details.visibility)
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun SunCycleSection(sunInfo: SunInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val now = LocalTime.now()
            val sunrise = sunInfo.sunrise
            val sunset = sunInfo.sunset

            val totalDaylight = Duration.between(sunrise, sunset).toMinutes().toFloat()
            val timeSinceSunrise = if (now.isAfter(sunrise)) Duration.between(sunrise, now).toMinutes().toFloat() else 0f
            val progress = (timeSinceSunrise / totalDaylight).coerceIn(0f, 1f)

            SunArc(progress = progress)
            Spacer(modifier = Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Wschód: ${sunrise.format(DateTimeFormatter.ofPattern("HH:mm"))}", fontWeight = FontWeight.Bold)
                Text("Zachód: ${sunset.format(DateTimeFormatter.ofPattern("HH:mm"))}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SunArc(progress: Float) {
    val stroke = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
    val sunColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(100.dp)) {
        val arcSize = Size(width = size.width * 0.8f, height = size.width * 0.8f)
        val arcTopLeft = Offset(x = (size.width - arcSize.width) / 2, y = size.height * 0.2f)

        drawArc(
            color = OnSurfaceVariant,
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
            color = sunColor,
            radius = 20f,
            center = Offset(sunX, sunY)
        )
    }
}


