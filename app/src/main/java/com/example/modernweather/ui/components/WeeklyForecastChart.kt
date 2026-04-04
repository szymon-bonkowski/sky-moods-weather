package com.example.modernweather.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modernweather.data.models.DailyForecast
import com.example.modernweather.data.models.TemperatureUnit
import com.example.modernweather.data.models.WeatherCondition
import com.example.modernweather.ui.screens.toFahrenheit
import com.example.modernweather.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs

private data class ForecastPoint(
    val index: Int,
    val date: LocalDate,
    val highTemp: Int,
    val lowTemp: Int,
    val condition: WeatherCondition,
    var xPosition: Float = 0f,
    var highYRaw: Float = 0f,
    var lowYRaw: Float = 0f,
    var highYPosition: Float = 0f,
    var lowYPosition: Float = 0f,
    var highIconRegion: Region = Region(0f, 0f, 0f, 0f),
    var lowIconRegion: Region = Region(0f, 0f, 0f, 0f),
    var highTempRegion: Region = Region(0f, 0f, 0f, 0f),
    var lowTempRegion: Region = Region(0f, 0f, 0f, 0f),
    var dayLabelRegion: Region = Region(0f, 0f, 0f, 0f)
)

private data class Region(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private data class LabelLayouts(
    val highTemp: TextLayoutResult,
    val highDegree: TextLayoutResult,
    val lowTemp: TextLayoutResult,
    val lowDegree: TextLayoutResult,
    val day: TextLayoutResult,
    val dot: TextLayoutResult?,
    val dayOfMonth: TextLayoutResult
)

private data class RegionTempLayouts(
    val high: TextLayoutResult,
    val low: TextLayoutResult
)

private data class ChartLayoutData(
    val points: List<ForecastPoint>,
    val highLinePaths: List<Path>,
    val lowLinePaths: List<Path>,
    val yTopPadding: Float,
    val yBottom: Float,
    val iconSize: Float
)

@Composable
fun WeeklyForecastChart(
    modifier: Modifier = Modifier,
    dailyForecasts: List<DailyForecast>,
    unit: TemperatureUnit
) {
    if (dailyForecasts.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val dayPainters = remember(dailyForecasts) {
        dailyForecasts.map { forecast ->
            painterResource(id = weatherConditionIconRes(forecast.conditionEnum, true))
        }
    }
    val nightPainters = remember(dailyForecasts) {
        dailyForecasts.map { forecast ->
            painterResource(id = weatherConditionIconRes(forecast.conditionEnum, false))
        }
    }
    val forecastKey = remember(dailyForecasts) {
        dailyForecasts.joinToString("|") { forecast ->
            "${forecast.date}:${forecast.highTemp}:${forecast.lowTemp}:${forecast.conditionEnum}"
        }
    }

    val animationPlayed = rememberSaveable(forecastKey) {
        mutableStateOf(false)
    }

    val animationProgress = remember(forecastKey) {
        Animatable(if (animationPlayed.value) 1f else 0f)
    }

    LaunchedEffect(forecastKey) {
        if (!animationPlayed.value) {
            animationProgress.snapTo(0f)
            animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 1500))
            animationPlayed.value = true
        }
    }

    val regionTempLayouts = remember(dailyForecasts, unit, textMeasurer) {
        val style = TextStyle(fontSize = 16.sp)
        dailyForecasts.map { forecast ->
            RegionTempLayouts(
                high = textMeasurer.measure(
                    text = "${toFahrenheitAware(forecast.highTemp, unit)}°",
                    style = style
                ),
                low = textMeasurer.measure(
                    text = "${toFahrenheitAware(forecast.lowTemp, unit)}°",
                    style = style
                )
            )
        }
    }

    val labelLayouts = remember(dailyForecasts, unit, textMeasurer) {
        val highLowStyle = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        val degreeStyle = highLowStyle
        val dayMonthStyle = TextStyle(color = OnSurfaceVariant, fontSize = 14.sp)
        dailyForecasts.map { forecast ->
            var dayOfWeekText = forecast.date.dayOfWeek.getDisplayName(
                JavaTextStyle.SHORT,
                locale
            ).replaceFirstChar { it.titlecase(locale) }
            if (dayOfWeekText == "Niedz.") dayOfWeekText = "Ndz."
            val dayOfWeekTextNoDot = dayOfWeekText.removeSuffix(".")
            val dot = if (dayOfWeekText.endsWith(".")) "." else ""
            val dayColor = if (forecast.date == LocalDate.now()) Color(0xFFFF6F6F) else OnSurfaceVariant
            val dayStyle = TextStyle(color = dayColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            LabelLayouts(
                highTemp = textMeasurer.measure(
                    text = toFahrenheitAware(forecast.highTemp, unit).toString(),
                    style = highLowStyle
                ),
                highDegree = textMeasurer.measure("°", style = degreeStyle),
                lowTemp = textMeasurer.measure(
                    text = toFahrenheitAware(forecast.lowTemp, unit).toString(),
                    style = highLowStyle
                ),
                lowDegree = textMeasurer.measure("°", style = degreeStyle),
                day = textMeasurer.measure(dayOfWeekTextNoDot, style = dayStyle),
                dot = if (dot.isNotEmpty()) textMeasurer.measure(dot, style = dayStyle) else null,
                dayOfMonth = textMeasurer.measure(forecast.date.dayOfMonth.toString(), style = dayMonthStyle)
            )
        }
    }

    val chartLayoutData = remember(dailyForecasts, unit, canvasSize, density, regionTempLayouts) {
        if (canvasSize == IntSize.Zero) return@remember null
        val sizeSnapshot = canvasSize
        with(density) {
            val chartSize = Size(sizeSnapshot.width.toFloat(), sizeSnapshot.height.toFloat())
            val xPadding = 24.dp.toPx()
            val yTopPadding = 15.dp.toPx()
            val yBottomPadding = 60.dp.toPx()
            val chartWidth = chartSize.width - 2 * xPadding
            val chartHeight = chartSize.height - yTopPadding - yBottomPadding
            val xStep = chartWidth / (dailyForecasts.size - 1).coerceAtLeast(1)
            val iconSize = 24.dp.toPx()
            val minIconSpacing = 36.dp.toPx()
            val tempTextHeight = 16.sp.toPx() + 4.dp.toPx()
            val (minTemp, maxTemp) = getMinMaxTemperatures(dailyForecasts, unit)
            val tempRange = (maxTemp - minTemp).coerceAtLeast(1f)

            val forecastPoints = dailyForecasts.mapIndexed { index, forecast ->
                val highTempF = toFahrenheitAware(forecast.highTemp, unit).toFloat()
                val lowTempF = toFahrenheitAware(forecast.lowTemp, unit).toFloat()
                val highYRaw = yTopPadding + (1f - (highTempF - minTemp) / tempRange) * chartHeight
                val lowYRaw = yTopPadding + (1f - (lowTempF - minTemp) / tempRange) * chartHeight
                ForecastPoint(
                    index = index,
                    date = forecast.date,
                    highTemp = forecast.highTemp,
                    lowTemp = forecast.lowTemp,
                    condition = forecast.conditionEnum,
                    xPosition = xPadding + index * xStep,
                    highYRaw = highYRaw,
                    lowYRaw = lowYRaw
                )
            }

            adjustForecastPositions(
                points = forecastPoints,
                minSpacing = minIconSpacing,
                iconSize = iconSize,
                textHeight = tempTextHeight,
                chartHeight = chartHeight,
                topPadding = yTopPadding
            )
            calculateElementRegions(
                points = forecastPoints,
                tempLayouts = regionTempLayouts,
                iconSize = iconSize,
                density = this
            )

            val gapRadius = iconSize / 2 + 4.dp.toPx()
            ChartLayoutData(
                points = forecastPoints,
                highLinePaths = buildLinePaths(forecastPoints, isHighTemp = true, gapRadius = gapRadius),
                lowLinePaths = buildLinePaths(forecastPoints, isHighTemp = false, gapRadius = gapRadius),
                yTopPadding = yTopPadding,
                yBottom = chartSize.height - yBottomPadding,
                iconSize = iconSize
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidthPx = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
        val xPaddingPx = with(density) { 24.dp.toPx() }
        val weekStepPx = ((viewportWidthPx - 2f * xPaddingPx) / 6f).coerceAtLeast(0f)
        val chartWidthPx = maxOf(
            viewportWidthPx,
            xPaddingPx * 2f + weekStepPx * (dailyForecasts.size - 1).coerceAtLeast(1)
        )
        val chartWidthDp = with(density) { chartWidthPx.toDp() }
        val currentDayIndex = remember(dailyForecasts) {
            val today = LocalDate.now()
            dailyForecasts.indexOfFirst { it.date == today }
                .takeIf { it >= 0 }
                ?: dailyForecasts.indexOfFirst { !it.date.isBefore(today) }.takeIf { it >= 0 }
                ?: 0
        }
        val initialScrollPx = remember(viewportWidthPx, chartWidthPx, weekStepPx, currentDayIndex) {
            val anchor = currentDayIndex * weekStepPx
            val maxScroll = (chartWidthPx - viewportWidthPx).coerceAtLeast(0f)
            anchor.coerceIn(0f, maxScroll).toInt()
        }
        val scrollState = rememberScrollState(initial = initialScrollPx)
        val nestedScrollConnection = remember(initialScrollPx) {
            object : NestedScrollConnection {
                fun distanceToNowBoundaryPx(): Float {
                    return (scrollState.value - initialScrollPx).toFloat().coerceAtLeast(0f)
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val pullingPast = available.x > 0f
                    if (!pullingPast) return Offset.Zero

                    val currentScroll = scrollState.value
                    val isInPast = currentScroll < initialScrollPx
                    val isAtNow = currentScroll == initialScrollPx

                    if (source == NestedScrollSource.SideEffect) {
                        if (isInPast) {
                            return available
                        }
                        val distanceToBoundary = distanceToNowBoundaryPx()
                        if (distanceToBoundary <= 0f) {
                            return available
                        }
                        val overflowPastNow = (available.x - distanceToBoundary).coerceAtLeast(0f)
                        if (overflowPastNow > 0f) {
                            return Offset(overflowPastNow, 0f)
                        }
                    }

                    if (isInPast || isAtNow) {
                        return Offset(available.x * 0.05f, 0f)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (available.x > 0f && scrollState.value <= initialScrollPx) {
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        LaunchedEffect(scrollState.isScrollInProgress, initialScrollPx) {
            if (!scrollState.isScrollInProgress && scrollState.value < initialScrollPx) {
                scrollState.animateScrollTo(initialScrollPx.coerceAtMost(scrollState.maxValue))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(chartWidthDp)
                    .fillMaxHeight()
                    .onSizeChanged { canvasSize = it }
            ) {
                val layoutData = chartLayoutData ?: return@Canvas
                drawGridLines(
                    points = layoutData.points,
                    yTop = layoutData.yTopPadding,
                    yBottom = layoutData.yBottom
                )
                drawTemperatureLines(
                    highLinePaths = layoutData.highLinePaths,
                    lowLinePaths = layoutData.lowLinePaths,
                    highColor = AccentYellow,
                    lowColor = AccentBlue,
                    progress = animationProgress.value
                )
                drawLabels(layoutData.points, labelLayouts, size)

                layoutData.points.forEachIndexed { index, point ->
                    drawWeatherIcon(
                        painter = dayPainters[index],
                        x = point.xPosition,
                        y = point.highYPosition,
                        iconSize = layoutData.iconSize,
                        alpha = animationProgress.value
                    )
                    drawWeatherIcon(
                        painter = nightPainters[index],
                        x = point.xPosition,
                        y = point.lowYPosition,
                        iconSize = layoutData.iconSize,
                        alpha = animationProgress.value
                    )
                }
            }
        }
    }
}

private fun adjustForecastPositions(
    points: List<ForecastPoint>,
    minSpacing: Float,
    iconSize: Float,
    textHeight: Float,
    chartHeight: Float,
    topPadding: Float
) {
    points.forEach { point ->
        point.highYPosition = point.highYRaw
        point.lowYPosition = point.lowYRaw
    }

    points.forEach { point ->
        val currentSpacing = abs(point.lowYPosition - point.highYPosition)

        if (currentSpacing < minSpacing) {
            val adjustment = (minSpacing - currentSpacing) / 2

            point.highYPosition -= adjustment
            point.lowYPosition += adjustment

            if (point.highYPosition < topPadding + iconSize/2) {
                val highOverflow = topPadding + iconSize/2 - point.highYPosition
                point.highYPosition = topPadding + iconSize/2
                point.lowYPosition += highOverflow
            }

            if (point.lowYPosition > topPadding + chartHeight - iconSize/2) {
                val lowOverflow = point.lowYPosition - (topPadding + chartHeight - iconSize/2)
                point.lowYPosition = topPadding + chartHeight - iconSize/2
                point.highYPosition -= lowOverflow
                point.highYPosition = point.highYPosition.coerceAtLeast(topPadding + iconSize/2)
            }
        }
    }

    points.forEach { point ->
        val highTextBottom = point.highYPosition - iconSize/2 - textHeight

        if (highTextBottom < topPadding) {
            val adjustment = topPadding - highTextBottom
            point.highYPosition += adjustment
            point.lowYPosition = (point.lowYPosition + adjustment).coerceAtMost(topPadding + chartHeight - iconSize/2)
        }
    }
}

private fun calculateElementRegions(
    points: List<ForecastPoint>,
    tempLayouts: List<RegionTempLayouts>,
    iconSize: Float,
    density: Density
) {
    with(density) {
        points.forEachIndexed { index, point ->
            val halfIconSize = iconSize / 2
            point.highIconRegion = Region(
                point.xPosition - halfIconSize,
                point.highYPosition - halfIconSize,
                point.xPosition + halfIconSize,
                point.highYPosition + halfIconSize
            )

            point.lowIconRegion = Region(
                point.xPosition - halfIconSize,
                point.lowYPosition - halfIconSize,
                point.xPosition + halfIconSize,
                point.lowYPosition + halfIconSize
            )

            val highTempLayout = tempLayouts[index].high

            point.highTempRegion = Region(
                point.xPosition - highTempLayout.size.width/2,
                point.highYPosition - halfIconSize - highTempLayout.size.height - 4.dp.toPx(),
                point.xPosition + highTempLayout.size.width/2,
                point.highYPosition - halfIconSize - 4.dp.toPx()
            )

            val lowTempLayout = tempLayouts[index].low

            point.lowTempRegion = Region(
                point.xPosition - lowTempLayout.size.width/2,
                point.lowYPosition + halfIconSize + 4.dp.toPx(),
                point.xPosition + lowTempLayout.size.width/2,
                point.lowYPosition + halfIconSize + lowTempLayout.size.height + 4.dp.toPx()
            )
        }
    }
}

private fun buildLinePaths(
    points: List<ForecastPoint>,
    isHighTemp: Boolean,
    gapRadius: Float
): List<Path> {
    if (points.size < 2) return emptyList()
    return buildList {
        for (i in 0 until points.size - 1) {
            val startPoint = points[i]
            val endPoint = points[i + 1]
            val startY = if (isHighTemp) startPoint.highYPosition else startPoint.lowYPosition
            val endY = if (isHighTemp) endPoint.highYPosition else endPoint.lowYPosition
            val startX = startPoint.xPosition
            val endX = endPoint.xPosition
            val goingRight = startX < endX
            val adjustedStartX = if (goingRight) startX + gapRadius else startX - gapRadius
            val adjustedEndX = if (goingRight) endX - gapRadius else endX + gapRadius

            if ((goingRight && adjustedStartX >= adjustedEndX) ||
                (!goingRight && adjustedStartX <= adjustedEndX)
            ) {
                continue
            }

            add(Path().apply {
                moveTo(adjustedStartX, startY)
                val midX = (adjustedStartX + adjustedEndX) / 2
                cubicTo(
                    x1 = midX, y1 = startY,
                    x2 = midX, y2 = endY,
                    x3 = adjustedEndX, y3 = endY
                )
            })
        }
    }
}

private fun DrawScope.drawWeatherIcon(
    painter: Painter,
    x: Float,
    y: Float,
    iconSize: Float,
    alpha: Float
) {
    val offset = Offset(x - iconSize / 2, y - iconSize / 2)
    translate(offset.x, offset.y) {
        with(painter) {
            draw(size = Size(iconSize, iconSize), alpha = alpha)
        }
    }
}

private fun DrawScope.drawTemperatureLines(
    highLinePaths: List<Path>,
    lowLinePaths: List<Path>,
    highColor: Color,
    lowColor: Color,
    progress: Float
) {
    drawPathSegments(paths = highLinePaths, color = highColor, progress = progress)
    drawPathSegments(paths = lowLinePaths, color = lowColor, progress = progress)
}

private fun DrawScope.drawPathSegments(
    paths: List<Path>,
    color: Color,
    progress: Float
) {
    val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    if (progress >= 1f) {
        paths.forEach { path ->
            drawPath(path = path, color = color, style = stroke)
        }
    } else {
        val segmentProgress = progress.coerceIn(0f, 1f)
        val pathMeasure = PathMeasure()
        paths.forEach { path ->
            val animatedPath = Path()
            pathMeasure.setPath(path, false)
            pathMeasure.getSegment(0f, pathMeasure.length * segmentProgress, animatedPath, true)
            drawPath(path = animatedPath, color = color, style = stroke)
        }
    }
}

private fun DrawScope.drawGridLines(
    points: List<ForecastPoint>,
    yTop: Float,
    yBottom: Float
) {
    val lineAlpha = 0.3f
    val lineWidth = 1.dp.toPx()
    val gapBuffer = 3.dp.toPx()

    points.forEach { point ->
        val sortedObstacles = listOf(
            point.highTempRegion,
            point.highIconRegion,
            point.lowIconRegion,
            point.lowTempRegion
        ).sortedBy { it.top }

        var currentY = yTop

        for (obstacle in sortedObstacles) {
            if (obstacle.top - gapBuffer > currentY) {
                drawLine(
                    color = OnSurfaceVariant.copy(alpha = lineAlpha),
                    start = Offset(point.xPosition, currentY),
                    end = Offset(point.xPosition, obstacle.top - gapBuffer),
                    strokeWidth = lineWidth
                )
            }
            currentY = obstacle.bottom + gapBuffer
        }

        if (yBottom > currentY) {
            drawLine(
                color = OnSurfaceVariant.copy(alpha = lineAlpha),
                start = Offset(point.xPosition, currentY),
                end = Offset(point.xPosition, yBottom),
                strokeWidth = lineWidth
            )
        }
    }
}

private fun DrawScope.drawLabels(
    points: List<ForecastPoint>,
    labelLayouts: List<LabelLayouts>,
    size: Size
) {
    points.forEachIndexed { index, point ->
        val layouts = labelLayouts[index]
        val highTempLayout = layouts.highTemp
        val degreeSymbolLayout = layouts.highDegree

        val highTempX = point.xPosition - highTempLayout.size.width / 2
        val highTempY = point.highYPosition - highTempLayout.size.height - 18.dp.toPx()
        drawText(highTempLayout, topLeft = Offset(highTempX, highTempY))
        drawText(degreeSymbolLayout, topLeft = Offset(highTempX + highTempLayout.size.width, highTempY))

        val lowTempLayout = layouts.lowTemp
        val lowDegreeLayout = layouts.lowDegree

        val lowTempX = point.xPosition - lowTempLayout.size.width / 2
        val lowTempY = point.lowYPosition + 15.dp.toPx()
        drawText(lowTempLayout, topLeft = Offset(lowTempX, lowTempY))
        drawText(lowDegreeLayout, topLeft = Offset(lowTempX + lowTempLayout.size.width, lowTempY))

        val dayLayout = layouts.day
        val dotLayout = layouts.dot

        val dayX = point.xPosition - dayLayout.size.width / 2
        val dayY = size.height - 50.dp.toPx()
        drawText(dayLayout, topLeft = Offset(dayX, dayY))

        if (dotLayout != null) {
            drawText(dotLayout, topLeft = Offset(dayX + dayLayout.size.width, dayY))
        }

        val dayOfMonthLayout = layouts.dayOfMonth

        drawText(
            dayOfMonthLayout,
            topLeft = Offset(point.xPosition - dayOfMonthLayout.size.width / 2, size.height - 32.dp.toPx())
        )
    }
}

private fun getMinMaxTemperatures(forecasts: List<DailyForecast>, unit: TemperatureUnit): Pair<Float, Float> {
    if (forecasts.isEmpty()) return 0f to 0f
    val allTemps = forecasts.flatMap {
        listOf(toFahrenheitAware(it.highTemp, unit).toFloat(), toFahrenheitAware(it.lowTemp, unit).toFloat())
    }
    val min = allTemps.minOrNull() ?: 0f
    val max = allTemps.maxOrNull() ?: 0f
    return (min - 5f) to (max + 5f)
}

private fun toFahrenheitAware(celsius: Int, unit: TemperatureUnit): Int =
    if (unit == TemperatureUnit.CELSIUS) celsius else toFahrenheit(celsius)
