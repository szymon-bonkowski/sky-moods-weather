package com.example.modernweather.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
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
import com.example.modernweather.ui.components.weatherConditionIconRes
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
    val density = LocalDensity.current

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        WeatherParticleSystem(condition = current.conditionEnum)

        Column(
            modifier = Modifier.padding(top = 0.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val tempTextStyle = TextStyle(
                fontSize = 96.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            val degreeTextStyle = TextStyle(
                fontSize = 52.sp,
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
                val tempCanvasHeight = remember(tempLayout, degreeLayout, density) {
                    with(density) {
                        (maxOf(tempLayout.size.height, degreeLayout.size.height).toFloat() + 4.dp.toPx()).toDp()
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(300.dp, tempCanvasHeight)) {
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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = current.condition,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            val displayFeelsLike = if (unit == TemperatureUnit.CELSIUS) current.feelsLike else toFahrenheit(current.feelsLike)
            Text(
                text = "Odczuwalna $displayFeelsLike° • ${current.temperatureComparison}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            HourlyWaveChart(
                hourlyForecast = hourly,
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
    val density = androidx.compose.ui.platform.LocalDensity.current

    val displayTemps = remember(hourlyForecast, unit) {
        hourlyForecast.map {
            if (unit == TemperatureUnit.CELSIUS) it.temperature else toFahrenheit(it.temperature)
        }
    }

    val fillGradientColors = remember(displayTemps) {
        val minTemp = displayTemps.minOrNull() ?: 0
        val maxTemp = displayTemps.maxOrNull() ?: 0
        val range = (maxTemp - minTemp).coerceAtLeast(1)

        val colors = displayTemps.map { temp ->
            val normalized = ((temp - minTemp).toFloat() / range).coerceIn(0f, 1f)
            androidx.compose.ui.graphics.lerp(
                start = AccentBlue,
                stop = Color(0xFFFF6F00),
                fraction = normalized
            )
        }

        if (colors.size == 1) colors + colors.first() else colors
    }

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
        painterResource(id = weatherConditionIconRes(it.conditionEnum))
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

    val edgePaddingPx = with(density) {
        val labelHalfWidthPx = maxOf(
            tempTextLayouts.maxOfOrNull { it.size.width + degreeLayout.size.width }?.toFloat() ?: 0f,
            timeTextLayouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
        ) / 2f
        max(16.dp.toPx(), labelHalfWidthPx + 12.dp.toPx())
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clipToBounds()
    ) {
        val scope = this
        val viewportWidthPx = with(density) { scope.maxWidth.toPx() }
        val pointSpacingPx = with(density) { 72.dp.toPx() }
        val chartWidthPx = max(
            viewportWidthPx,
            edgePaddingPx * 2 + pointSpacingPx * (hourlyForecast.size - 1).coerceAtLeast(1)
        )
        val chartWidthDp = with(density) { chartWidthPx.toDp() }
        val currentHourIndex = remember(hourlyForecast) {
            hourlyForecast.indexOfFirst { it.time.hour == java.time.LocalTime.now().hour }
                .takeIf { it >= 0 } ?: 0
        }
        val initialScrollPx = remember(viewportWidthPx, chartWidthPx, pointSpacingPx, currentHourIndex) {
            val anchor = edgePaddingPx + currentHourIndex * pointSpacingPx
            val maxScroll = (chartWidthPx - viewportWidthPx).coerceAtLeast(0f)
            (anchor - viewportWidthPx / 2f).coerceIn(0f, maxScroll).toInt()
        }
        val scrollState = rememberScrollState(initial = initialScrollPx)
        val baselineAxisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(chartWidthDp)
                    .fillMaxHeight()
            ) {
                val drawScope = this
                val points = hourlyForecast.size
                if (points < 2) return@Canvas

                val dataMinTemp = displayTemps.minOrNull() ?: 0
                val dataMaxTemp = displayTemps.maxOrNull() ?: 0
                val dataRange = (dataMaxTemp - dataMinTemp).coerceAtLeast(1)
                val tempMargin = (dataRange * 0.3f).toInt().coerceAtLeast(3)
                val minTemp = dataMinTemp - tempMargin
                val maxTemp = dataMaxTemp + tempMargin
                val tempRange = (maxTemp - minTemp).coerceAtLeast(1)
                val path = Path()
                val pathPoints = mutableListOf<Offset>()
                val xStep = (size.width - 2 * edgePaddingPx) / (points - 1)
                val yPaddingTop = 44.dp.toPx()
                val timeLabelHeight = timeTextLayouts.maxOfOrNull { it.size.height.toFloat() } ?: 40f
                val baselineY = size.height - timeLabelHeight - 16.dp.toPx()
                val chartHeight = baselineY - yPaddingTop

                hourlyForecast.forEachIndexed { index, forecast ->
                    val temp = if (unit == TemperatureUnit.CELSIUS) forecast.temperature else toFahrenheit(forecast.temperature)
                    val x = edgePaddingPx + index * xStep
                    val y = baselineY - ((temp - minTemp).toFloat() / tempRange) * chartHeight
                    pathPoints.add(Offset(x, y))
                }

                path.moveTo(pathPoints.first().x, pathPoints.first().y)
                for (i in 0 until pathPoints.size - 1) {
                    val p0 = if (i == 0) pathPoints[i] else pathPoints[i - 1]
                    val p1 = pathPoints[i]
                    val p2 = pathPoints[i + 1]
                    val p3 = if (i + 1 == pathPoints.size - 1) pathPoints[i + 1] else pathPoints[i + 2]

                    val tension = 0.15f
                    val cp1x = p1.x + (p2.x - p0.x) * tension
                    val cp1y = p1.y + (p2.y - p0.y) * tension
                    val cp2x = p2.x - (p3.x - p1.x) * tension
                    val cp2y = p2.y - (p3.y - p1.y) * tension

                    path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }
                
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(pathPoints.last().x, baselineY)
                    lineTo(pathPoints.first().x, baselineY)
                    close()
                }

                drawScope.drawPath(
                    path = fillPath,
                    brush = Brush.horizontalGradient(
                        colors = fillGradientColors.map { it.copy(alpha = 0.18f) }
                    )
                )

                // Optional: Draw a subtle baseline axis
                drawScope.drawLine(
                    color = baselineAxisColor,
                    start = Offset(pathPoints.first().x, baselineY),
                    end = Offset(pathPoints.last().x, baselineY),
                    strokeWidth = 1.dp.toPx()
                )

                drawScope.drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(colors = fillGradientColors),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                pathPoints.forEachIndexed { index, point ->
                    val tempTextLayout: TextLayoutResult = tempTextLayouts[index]
                    val tempTextWidth = tempTextLayout.size.width
                    val degreeWidth = degreeLayout.size.width
                    val totalWidth = tempTextWidth + degreeWidth
                    val startX = point.x - totalWidth / 2f
                    val tempBlockHeight = max(tempTextLayout.size.height, degreeLayout.size.height).toFloat()
                    val iconSize = 18.dp.toPx()
                    val iconTop = point.y - iconSize - 2.dp.toPx()
                    val textY = iconTop - tempBlockHeight - 6.dp.toPx()

                    drawScope.drawText(
                        textLayoutResult = tempTextLayout,
                        topLeft = Offset(startX, textY)
                    )

                    drawScope.drawText(
                        textLayoutResult = degreeLayout,
                        topLeft = Offset(startX + tempTextWidth, textY)
                    )

                    val painter = iconPainters[index]
                    drawScope.translate(left = point.x - iconSize / 2, top = iconTop) {
                        with(painter) {
                            draw(Size(iconSize, iconSize))
                        }
                    }

                    val timeTextLayout: TextLayoutResult = timeTextLayouts[index]
                    drawScope.drawText(
                        textLayoutResult = timeTextLayout,
                        topLeft = Offset(point.x - timeTextLayout.size.width / 2, baselineY + 8.dp.toPx())
                    )
                }
            }
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
