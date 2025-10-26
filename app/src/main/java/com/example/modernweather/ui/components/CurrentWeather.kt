package com.example.modernweather.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modernweather.data.models.CurrentWeather
import com.example.modernweather.data.models.HourlyForecast
import com.example.modernweather.data.models.TemperatureUnit
import com.example.modernweather.data.models.WeatherCondition
import com.example.modernweather.ui.screens.toFahrenheit
import com.example.modernweather.ui.theme.AccentBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.random.Random

@OptIn(ExperimentalTextApi::class)
@Composable
fun CurrentWeatherSection(current: CurrentWeather, hourly: List<HourlyForecast>, unit: TemperatureUnit) {
    val displayTemp = if (unit == TemperatureUnit.CELSIUS) current.temperature else toFahrenheit(current.temperature)
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        WeatherParticleSystem(condition = current.conditionEnum)

        Column(
            modifier = Modifier.padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val tempTextStyle = TextStyle(
                fontSize = 120.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            val degreeTextStyle = TextStyle(
                fontSize = 60.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            AnimatedContent(
                targetState = displayTemp.toString(),
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                            (slideOutVertically { height -> -height } + fadeOut())
                },
                label = "temp_anim"
            ) { temp ->
                val tempLayout = remember(temp, tempTextStyle) {
                    textMeasurer.measure(temp, style = tempTextStyle)
                }
                val degreeLayout = remember(degreeTextStyle) {
                    textMeasurer.measure("°", style = degreeTextStyle)
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(300.dp, 140.dp)) {
                        val centerX = size.width / 2
                        val tempX = centerX - tempLayout.size.width / 2
                        val degreeX = tempX + tempLayout.size.width

                        val tempY = 0f
                        val degreeY = tempY + (tempLayout.size.height * 0.09f) - 1f

                        drawText(
                            textLayoutResult = tempLayout,
                            topLeft = Offset(tempX, tempY)
                        )

                        drawText(
                            textLayoutResult = degreeLayout,
                            topLeft = Offset(degreeX, degreeY)
                        )
                    }
                }
            }

            Text(
                text = current.condition,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            HourlyWaveChart(
                hourlyForecast = hourly.take(8),
                unit = unit
            )
        }
    }
}


@OptIn(ExperimentalTextApi::class)
@Composable
private fun HourlyWaveChart(hourlyForecast: List<HourlyForecast>, unit: TemperatureUnit) {
    if (hourlyForecast.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    val tempTextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    val timeTextStyle = TextStyle(
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val iconPainters = hourlyForecast.map {
        painterResource(id = mapConditionToIcon(it.conditionEnum))
    }

    val tempTextLayouts = remember(hourlyForecast, unit, tempTextStyle) {
        hourlyForecast.map {
            val temp = if (unit == TemperatureUnit.CELSIUS) it.temperature else toFahrenheit(it.temperature)
            textMeasurer.measure(
                temp.toString(),
                style = tempTextStyle
            )
        }
    }

    val degreeLayout = remember(tempTextStyle) {
        textMeasurer.measure("°", style = tempTextStyle)
    }

    val timeTextLayouts = remember(hourlyForecast, timeTextStyle) {
        hourlyForecast.map {
            textMeasurer.measure(
                it.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = timeTextStyle
            )
        }
    }

    val currentOnSurfaceColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .padding(horizontal = 16.dp)) {
        val points = hourlyForecast.size
        if (points < 2) return@Canvas

        val allTemps = hourlyForecast.map { if (unit == TemperatureUnit.CELSIUS) it.temperature else toFahrenheit(it.temperature) }
        val minTemp = allTemps.minOrNull() ?: 0
        val maxTemp = allTemps.maxOrNull() ?: 0
        val tempRange = (maxTemp - minTemp).coerceAtLeast(1)

        val path = Path()
        val pathPoints = mutableListOf<Offset>()

        val xStep = size.width / (points - 1)
        val yPadding = size.height * 0.3f
        val chartHeight = size.height - yPadding

        hourlyForecast.forEachIndexed { index, forecast ->
            val temp = if (unit == TemperatureUnit.CELSIUS) forecast.temperature else toFahrenheit(forecast.temperature)
            val x = index * xStep
            val y = chartHeight - ((temp - minTemp).toFloat() / tempRange) * (chartHeight - yPadding)
            pathPoints.add(Offset(x, y))
        }

        path.moveTo(pathPoints.first().x, pathPoints.first().y)
        for (i in 0 until pathPoints.size - 1) {
            val p1 = pathPoints[i]
            val p2 = pathPoints[i + 1]
            val controlPoint1 = Offset((p1.x + p2.x) / 2f, p1.y)
            val controlPoint2 = Offset((p1.x + p2.x) / 2f, p2.y)
            path.cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p2.x, p2.y)
        }

        drawPath(
            path = path,
            color = AccentBlue,
            style = Stroke(width = 2.dp.toPx())
        )

        val fillPath = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(AccentBlue.copy(alpha = 0.2f), Color.Transparent),
                endY = size.height
            )
        )

        pathPoints.forEachIndexed { index, point ->
            val tempTextLayout: TextLayoutResult = tempTextLayouts[index]
            val tempTextWidth = tempTextLayout.size.width
            val degreeWidth = degreeLayout.size.width
            val totalWidth = tempTextWidth + degreeWidth
            val startX = point.x - totalWidth / 2f
            val textY = point.y - 35.dp.toPx()

            drawText(
                textLayoutResult = tempTextLayout,
                topLeft = Offset(startX, textY)
            )

            drawText(
                textLayoutResult = degreeLayout,
                topLeft = Offset(startX + tempTextWidth, textY)
            )

            with(iconPainters[index]) {
                val iconSize = 20.dp.toPx()
                translate(left = point.x - iconSize / 2, top = point.y - 15.dp.toPx()) {
                    draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(currentOnSurfaceColor))
                }
            }

            val timeTextLayout: TextLayoutResult = timeTextLayouts[index]
            drawText(
                textLayoutResult = timeTextLayout,
                topLeft = Offset(point.x - timeTextLayout.size.width / 2, size.height - timeTextLayout.size.height)
            )
        }
    }
}


@Composable
fun WeatherParticleSystem(condition: WeatherCondition) {
    val particles = remember { mutableStateListOf<Particle>() }
    val isRainOrSnow = remember(condition) {
        condition in listOf(
            WeatherCondition.DAY_RAIN_LIGHT, WeatherCondition.DAY_RAIN_MEDIUM, WeatherCondition.DAY_RAIN_HEAVY,
            WeatherCondition.NIGHT_RAIN_LIGHT, WeatherCondition.NIGHT_RAIN_MEDIUM,
            WeatherCondition.DAY_SNOW, WeatherCondition.NIGHT_SNOW
        )
    }

    if (isRainOrSnow) {
        LaunchedEffect(condition) {
            while (isActive) {
                particles.add(Particle(isSnow = condition == WeatherCondition.DAY_SNOW || condition == WeatherCondition.NIGHT_SNOW))
                if (particles.size > 100) {
                    particles.removeAt(0)
                }
                delay(100)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { particle ->
                particle.update(size.height)
                drawCircle(
                    color = particle.color,
                    radius = particle.radius,
                    center = Offset(particle.x * size.width, particle.y)
                )
            }
        }
    }
}


private class Particle(
    val isSnow: Boolean,
    var x: Float = Random.nextFloat(),
    var y: Float = 0f,
    var radius: Float = if (isSnow) Random.nextFloat() * 2f + 2f else Random.nextFloat() * 1f + 1f,
    var color: Color = (if (isSnow) Color.White else AccentBlue).copy(alpha = Random.nextFloat() * 0.5f + 0.5f),
    private var ySpeed: Float = if (isSnow) Random.nextFloat() * 1.5f + 1f else Random.nextFloat() * 4f + 4f,
    private var xDrift: Float = if (isSnow) Random.nextFloat() - 0.5f else 0f
) {
    fun update(canvasHeight: Float) {
        y += ySpeed
        x += xDrift * 0.01f
        if (y > canvasHeight) {
            y = 0f
            x = Random.nextFloat()
        }
    }
}

fun mapConditionToIcon(condition: WeatherCondition): Int {
    return when (condition) {
        WeatherCondition.DAY_SUNNY -> com.example.modernweather.R.drawable.day
        WeatherCondition.DAY_PARTLY_CLOUDY -> com.example.modernweather.R.drawable.cloudy_day
        WeatherCondition.DAY_CLOUDY -> com.example.modernweather.R.drawable.cloudy
        WeatherCondition.DAY_RAIN_LIGHT -> com.example.modernweather.R.drawable.light_rain_day
        WeatherCondition.DAY_RAIN_MEDIUM -> com.example.modernweather.R.drawable.medium_rain_day
        WeatherCondition.DAY_RAIN_HEAVY -> com.example.modernweather.R.drawable.heavy_rain
        WeatherCondition.DAY_SNOW -> com.example.modernweather.R.drawable.snow_day
        WeatherCondition.DAY_FOG -> com.example.modernweather.R.drawable.fog_day
        WeatherCondition.DAY_FOG_CLOUDY -> com.example.modernweather.R.drawable.fog_cloudy
        WeatherCondition.DAY_THUNDERSTORM -> com.example.modernweather.R.drawable.thunderstorm_day
        WeatherCondition.DAY_THUNDERSTORM_HEAVY -> com.example.modernweather.R.drawable.thunderstorm_heavy_rain
        WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT -> com.example.modernweather.R.drawable.thunderstorm_light_rain_day
        WeatherCondition.DAY_THUNDERSTORM_RAIN_MEDIUM -> com.example.modernweather.R.drawable.thunderstorm_medium_rain_day
        WeatherCondition.DAY_WIND -> com.example.modernweather.R.drawable.wind_day
        WeatherCondition.DAY_WIND_CLOUDY -> com.example.modernweather.R.drawable.wind_cloudy
        WeatherCondition.NIGHT_CLEAR -> com.example.modernweather.R.drawable.night
        WeatherCondition.NIGHT_PARTLY_CLOUDY -> com.example.modernweather.R.drawable.cloudy_night
        WeatherCondition.NIGHT_CLOUDY -> com.example.modernweather.R.drawable.cloudy_night
        WeatherCondition.NIGHT_RAIN_LIGHT -> com.example.modernweather.R.drawable.light_rain_night
        WeatherCondition.NIGHT_RAIN_MEDIUM -> com.example.modernweather.R.drawable.medium_rain_night
        WeatherCondition.NIGHT_SNOW -> com.example.modernweather.R.drawable.snow_night
        WeatherCondition.NIGHT_FOG -> com.example.modernweather.R.drawable.fog_night
        WeatherCondition.NIGHT_THUNDERSTORM -> com.example.modernweather.R.drawable.thunderstorm_night
        WeatherCondition.NIGHT_THUNDERSTORM_RAIN_LIGHT -> com.example.modernweather.R.drawable.thunderstorm_light_rain_night
        WeatherCondition.NIGHT_THUNDERSTORM_MEDIUM_RAIN -> com.example.modernweather.R.drawable.thunderstorm_medium_rain_night
        WeatherCondition.NIGHT_WIND -> com.example.modernweather.R.drawable.wind_night
        else -> com.example.modernweather.R.drawable.cloudy
    }
}