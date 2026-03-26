package com.example.modernweather.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
    val nowcastAssessment by viewModel.nowcastAssessmentState.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "current_weather") {
            CurrentWeatherSection(current = data.currentWeather, hourly = data.hourlyForecast, unit = unit)
        }

        data.alert?.let { alert ->
            item(key = "alert_${alert.id}") {
                AlertCard(alert)
            }
        }


        item(key = "weekly_forecast") {
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

        item(key = "details_aqi") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailsGrid(details = data.weatherDetails)
                AqiSection(details = data.weatherDetails)
            }
        }

        item(key = "local_nowcast_status") {
            LocalNowcastCard(assessment = nowcastAssessment)
        }

        item(key = "radar") {
            RadarCard(onClick = onNavigateToRadar)
        }

        item(key = "sun_cycle") {
            SunCycleSection(sunInfo = stableSunInfo, viewModel = viewModel)
        }
    }
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
    SunCycle(sunInfo = sunInfo, viewModel = viewModel)
}

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
