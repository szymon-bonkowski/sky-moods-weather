package com.example.modernweather.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "PROGNOZA GODZINOWA",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, bottom = 2.dp)
            )

            HourlySimpleList(
                hourlyForecast = hourly,
                unit = unit
            )
        }
    }
}

@Composable
private fun HourlySimpleList(hourlyForecast: List<HourlyForecast>, unit: TemperatureUnit) {
    if (hourlyForecast.isEmpty()) return

    val currentHourIndex = remember(hourlyForecast) {
        val now = java.time.LocalTime.now().hour
        hourlyForecast.indexOfFirst { it.time.hour == now }.coerceAtLeast(0)
    }

    val scrollState = rememberScrollState()
    
    LaunchedEffect(currentHourIndex) {
        scrollState.scrollTo((currentHourIndex * 64 * 2.5).toInt())
    }

    val chartShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(chartShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = chartShape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            hourlyForecast.forEach { forecast ->
                val displayTemp = if (unit == TemperatureUnit.CELSIUS) forecast.temperature else toFahrenheit(forecast.temperature)
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.width(48.dp)
                ) {
                    Text(
                        text = forecast.time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Image(
                        painter = painterResource(id = weatherConditionIconRes(forecast.conditionEnum)),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    
                    Text(
                        text = "$displayTemp°",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun HourlyWaveChart(hourlyForecast: List<HourlyForecast>, unit: TemperatureUnit) {
    if (hourlyForecast.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val displayTemps = remember(hourlyForecast, unit) {
        hourlyForecast.map { forecast ->
            if (unit == TemperatureUnit.CELSIUS) forecast.temperature else toFahrenheit(forecast.temperature)
        }
    }

    val lineGradientColors = remember(displayTemps) {
        val minTemp = displayTemps.minOrNull() ?: 0
        val maxTemp = displayTemps.maxOrNull() ?: 0
        val range = (maxTemp - minTemp).coerceAtLeast(1)

        val palette = displayTemps.map { temp ->
            val normalized = ((temp - minTemp).toFloat() / range).coerceIn(0f, 1f)
            androidx.compose.ui.graphics.lerp(
                start = AccentBlue,
                stop = Color(0xFFFFC547),
                fraction = normalized
            )
        }

        if (palette.size == 1) palette + palette.first() else palette
    }

    val tempTextStyle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    val degreeTextStyle = tempTextStyle.copy(fontSize = 12.sp)
    val timeTextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f)
    )
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val pointFillColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)

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

    val degreeLayout = remember(degreeTextStyle) {
        textMeasurer.measure("°", style = degreeTextStyle)
    }

    val timeTextLayouts = remember(hourlyForecast, timeTextStyle, timeFormatter) {
        hourlyForecast.map { forecast ->
            textMeasurer.measure(
                forecast.time.format(timeFormatter),
                style = timeTextStyle
            )
        }
    }

    val chartShape = RoundedCornerShape(24.dp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(chartShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = chartShape
            )
            .clipToBounds()
    ) {
        val viewportWidthPx = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
        val pointSpacingPx = with(density) { 64.dp.toPx() }
        val edgePaddingPx = with(density) {
            val labelHalfWidthPx = maxOf(
                tempTextLayouts.maxOfOrNull { it.size.width + degreeLayout.size.width }?.toFloat() ?: 0f,
                timeTextLayouts.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
            ) / 2f
            max(18.dp.toPx(), labelHalfWidthPx + 10.dp.toPx())
        }
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
            (anchor - viewportWidthPx * 0.5f).coerceIn(0f, maxScroll).toInt()
        }
        val scrollState = rememberScrollState(initial = initialScrollPx)

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
                val pointCount = hourlyForecast.size
                if (pointCount < 2) return@Canvas

                val dataMinTemp = displayTemps.minOrNull() ?: 0
                val dataMaxTemp = displayTemps.maxOrNull() ?: 0
                val dataRange = (dataMaxTemp - dataMinTemp).coerceAtLeast(1)
                val tempMargin = (dataRange * 0.22f).toInt().coerceAtLeast(2)
                val minTemp = dataMinTemp - tempMargin
                val maxTemp = dataMaxTemp + tempMargin
                val tempRange = (maxTemp - minTemp).coerceAtLeast(1)
                val xStep = (size.width - 2 * edgePaddingPx) / (pointCount - 1)
                val chartTop = 44.dp.toPx()
                val timeBandHeight = (timeTextLayouts.maxOfOrNull { it.size.height.toFloat() } ?: 0f) + 10.dp.toPx()
                val chartBottom = size.height - timeBandHeight - 4.dp.toPx()
                val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
                val pathPoints = mutableListOf<Offset>()

                displayTemps.forEachIndexed { index, temp ->
                    val x = edgePaddingPx + index * xStep
                    val y = chartBottom - ((temp - minTemp).toFloat() / tempRange) * chartHeight
                    pathPoints.add(Offset(x, y))
                }

                val path = Path().apply {
                    moveTo(pathPoints.first().x, pathPoints.first().y)
                    for (i in 0 until pathPoints.lastIndex) {
                        val p0 = if (i == 0) pathPoints[i] else pathPoints[i - 1]
                        val p1 = pathPoints[i]
                        val p2 = pathPoints[i + 1]
                        val p3 = if (i + 1 == pathPoints.lastIndex) pathPoints[i + 1] else pathPoints[i + 2]

                        val tension = 0.18f
                        val cp1x = p1.x + (p2.x - p0.x) * tension
                        val cp1y = p1.y + (p2.y - p0.y) * tension
                        val cp2x = p2.x - (p3.x - p1.x) * tension
                        val cp2y = p2.y - (p3.y - p1.y) * tension

                        cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(pathPoints.last().x, chartBottom)
                    lineTo(pathPoints.first().x, chartBottom)
                    close()
                }

                for (step in 0..2) {
                    val y = chartTop + chartHeight * (step / 2f)
                    drawLine(
                        color = gridColor,
                        start = Offset(edgePaddingPx, y),
                        end = Offset(size.width - edgePaddingPx, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.linearGradient(
                        colors = lineGradientColors.map { it.copy(alpha = 0.28f) },
                        start = Offset(edgePaddingPx, chartTop),
                        end = Offset(size.width - edgePaddingPx, chartBottom)
                    )
                )

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        startY = chartTop,
                        endY = chartBottom
                    )
                )

                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(colors = lineGradientColors),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                pathPoints.forEachIndexed { index, point ->
                    val pointColor = lineGradientColors[index.coerceAtMost(lineGradientColors.lastIndex)]
                    drawCircle(
                        color = pointFillColor,
                        radius = 5.5.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = pointColor,
                        radius = 3.2.dp.toPx(),
                        center = point
                    )

                    val tempTextLayout: TextLayoutResult = tempTextLayouts[index]
                    val tempTextWidth = tempTextLayout.size.width
                    val degreeWidth = degreeLayout.size.width
                    val totalWidth = tempTextWidth + degreeWidth
                    val tempStartX = point.x - totalWidth / 2f
                    val tempBlockHeight = max(tempTextLayout.size.height, degreeLayout.size.height).toFloat()
                    val iconSize = 18.dp.toPx()
                    val iconTop = point.y - iconSize - 4.dp.toPx()
                    val tempY = (iconTop - tempBlockHeight - 4.dp.toPx()).coerceAtLeast(3.dp.toPx())

                    drawText(
                        textLayoutResult = tempTextLayout,
                        topLeft = Offset(tempStartX, tempY)
                    )

                    drawText(
                        textLayoutResult = degreeLayout,
                        topLeft = Offset(tempStartX + tempTextWidth, tempY)
                    )

                    val painter = iconPainters[index]
                    translate(left = point.x - iconSize / 2, top = iconTop) {
                        with(painter) {
                            draw(Size(iconSize, iconSize), alpha = 0.96f)
                        }
                    }

                    val timeTextLayout: TextLayoutResult = timeTextLayouts[index]
                    drawText(
                        textLayoutResult = timeTextLayout,
                        topLeft = Offset(
                            point.x - timeTextLayout.size.width / 2,
                            chartBottom + 8.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherParticleSystem(condition: WeatherCondition) {
    val particles = remember { mutableStateListOf<Particle>() }
    val particlePool = remember { mutableListOf<Particle>() }
    val isRainOrSnow = remember(condition) {
        condition in listOf(
            WeatherCondition.DAY_RAIN_LIGHT, WeatherCondition.DAY_RAIN_MEDIUM, WeatherCondition.DAY_RAIN_HEAVY,
            WeatherCondition.NIGHT_RAIN_LIGHT, WeatherCondition.NIGHT_RAIN_MEDIUM,
            WeatherCondition.DAY_SNOW, WeatherCondition.NIGHT_SNOW
        )
    }

    if (isRainOrSnow) {
        LaunchedEffect(condition) {
            val isSnow = condition == WeatherCondition.DAY_SNOW || condition == WeatherCondition.NIGHT_SNOW
            while (isActive) {
                val particle = if (particlePool.isNotEmpty()) {
                    particlePool.removeAt(particlePool.size - 1).apply { reset(isSnow) }
                } else {
                    Particle(isSnow = isSnow)
                }
                particles.add(particle)
                if (particles.size > 100) {
                    val removed = particles.removeAt(0)
                    particlePool.add(removed)
                }
                delay(100)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasHeight = size.height
            val canvasWidth = size.width

            particles.forEach { particle ->
                particle.update(canvasHeight)
                drawCircle(
                    color = particle.color,
                    radius = particle.radius,
                    center = Offset(particle.x * canvasWidth, particle.y)
                )
            }
        }
    }
}

private class Particle(
    var isSnow: Boolean,
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

    fun reset(snow: Boolean) {
        isSnow = snow
        x = Random.nextFloat()
        y = 0f
        radius = if (isSnow) Random.nextFloat() * 2f + 2f else Random.nextFloat() * 1f + 1f
        color = (if (isSnow) Color.White else AccentBlue).copy(alpha = Random.nextFloat() * 0.5f + 0.5f)
        ySpeed = if (isSnow) Random.nextFloat() * 1.5f + 1f else Random.nextFloat() * 4f + 4f
        xDrift = if (isSnow) Random.nextFloat() - 0.5f else 0f
    }
}
