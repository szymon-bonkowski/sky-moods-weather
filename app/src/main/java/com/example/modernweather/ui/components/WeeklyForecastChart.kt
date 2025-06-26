package com.example.modernweather.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.modernweather.R
import com.example.modernweather.data.models.DailyForecast
import com.example.modernweather.data.models.TemperatureUnit
import com.example.modernweather.data.models.WeatherCondition
import com.example.modernweather.ui.screens.toFahrenheit
import com.example.modernweather.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private data class ChartDataPoint(val x: Float, val y: Float)

@Composable
fun WeeklyForecastChart(
    modifier: Modifier = Modifier,
    dailyForecasts: List<DailyForecast>,
    unit: TemperatureUnit
) {
    if (dailyForecasts.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val animationProgress = remember { Animatable(0f) }

    val dayPainters = dailyForecasts.map { painterResource(id = mapConditionToPng(it.conditionEnum, true)) }
    val nightPainters = dailyForecasts.map { painterResource(id = mapConditionToPng(it.conditionEnum, false)) }

    LaunchedEffect(dailyForecasts) {
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 1500))
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val (minTemp, maxTemp) = getMinMaxTemperatures(dailyForecasts, unit)
        val tempRange = (maxTemp - minTemp).coerceAtLeast(1f)

        val xPadding = 24.dp.toPx()
        val yTopPadding = 15.dp.toPx()
        val yBottomPadding = 60.dp.toPx()

        val chartWidth = size.width - 2 * xPadding
        val chartHeight = size.height - yTopPadding - yBottomPadding
        val xStep = chartWidth / (dailyForecasts.size - 1).coerceAtLeast(1)

        fun yForTemp(temp: Float): Float = yTopPadding + (1f - (temp - minTemp) / tempRange) * chartHeight

        val highTempCoords = dailyForecasts.mapIndexed { index, forecast ->
            ChartDataPoint(xPadding + index * xStep, yForTemp(toFahrenheitAware(forecast.highTemp, unit).toFloat()))
        }
        val lowTempCoords = dailyForecasts.mapIndexed { index, forecast ->
            ChartDataPoint(xPadding + index * xStep, yForTemp(toFahrenheitAware(forecast.lowTemp, unit).toFloat()))
        }

        val highTempPath = createSmoothPath(highTempCoords)
        val lowTempPath = createSmoothPath(lowTempCoords)

        val iconRadius = 12.dp.toPx()
        val gapSize = 4.dp.toPx()

        drawPathWithGaps(highTempCoords, AccentYellow, animationProgress.value, iconRadius + gapSize)
        drawPathWithGaps(lowTempCoords, AccentBlue, animationProgress.value, iconRadius + gapSize)

        dailyForecasts.forEachIndexed { index, forecast ->
            val highPoint = highTempCoords[index]
            val lowPoint = lowTempCoords[index]

            drawTextLabels(
                textMeasurer = textMeasurer,
                forecast = forecast,
                highPoint = highPoint,
                lowPoint = lowPoint,
                unit = unit,
                size = size,
                yBottomPadding = yBottomPadding
            )
        }
        dailyForecasts.forEachIndexed { index, _ ->
            drawImageWithCutout(dayPainters[index], highTempCoords[index], animationProgress.value)
            drawImageWithCutout(nightPainters[index], lowTempCoords[index], animationProgress.value)
        }
    }
}

private fun DrawScope.drawImageWithCutout(painter: Painter, center: ChartDataPoint, alpha: Float) {
    val iconSize = 24.dp.toPx()
    val offset = Offset(center.x - iconSize / 2, center.y - iconSize / 2)

    translate(offset.x, offset.y) {
        with(painter) {
            draw(size = Size(iconSize, iconSize), alpha = alpha)
        }
    }
}


private fun DrawScope.drawTextLabels(
    textMeasurer: TextMeasurer, forecast: DailyForecast, highPoint: ChartDataPoint, lowPoint: ChartDataPoint,
    unit: TemperatureUnit, size: Size, yBottomPadding: Float
) {
    val onSurfaceColor = Color.White
    val onSurfaceVariantColor = OnSurfaceVariant
    val isTodayColor = Color(0xFFFF6F6F)

    val tempStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

    val highTempText = toFahrenheitAware(forecast.highTemp, unit).toString()
    val highTempLayout = textMeasurer.measure(highTempText, style = tempStyle.copy(color = onSurfaceColor))
    val degreeSymbolLayout = textMeasurer.measure("°", style = tempStyle.copy(color = onSurfaceColor))

    val highTempTopLeft = Offset(highPoint.x - highTempLayout.size.width / 2, highPoint.y - highTempLayout.size.height - 18.dp.toPx())
    val degreeTopLeft = Offset(highPoint.x + highTempLayout.size.width / 2, highTempTopLeft.y)

    drawText(highTempLayout, topLeft = highTempTopLeft)
    drawText(degreeSymbolLayout, topLeft = degreeTopLeft)

    val lowTempText = toFahrenheitAware(forecast.lowTemp, unit).toString()
    val lowTempStyle = tempStyle.copy(color = onSurfaceColor)
    val lowTempLayout = textMeasurer.measure(lowTempText, style = lowTempStyle)
    val lowDegreeLayout = textMeasurer.measure("°", style = tempStyle.copy(color = onSurfaceColor))

    val lowTempTopLeft = Offset(lowPoint.x - lowTempLayout.size.width / 2, lowPoint.y + 15.dp.toPx())
    val lowDegreeTopLeft = Offset(lowPoint.x + lowTempLayout.size.width / 2, lowTempTopLeft.y)

    drawText(lowTempLayout, topLeft = lowTempTopLeft)
    drawText(lowDegreeLayout, topLeft = lowDegreeTopLeft)

    var dayOfWeekText = forecast.date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.forLanguageTag("pl")).replaceFirstChar { it.titlecase(Locale.forLanguageTag("pl")) }
    if (dayOfWeekText == "Niedz.") dayOfWeekText = "Ndz."
    val dayOfWeekTextNoDot = dayOfWeekText.removeSuffix(".")
    val dot = if (dayOfWeekText.endsWith(".")) "." else ""
    val isToday = forecast.date == LocalDate.now()
    val dayOfWeekLayout = textMeasurer.measure(dayOfWeekTextNoDot, style = TextStyle(color = if (isToday) isTodayColor else onSurfaceVariantColor, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    val dotLayout = if (dot.isNotEmpty()) textMeasurer.measure(dot, style = TextStyle(color = if (isToday) isTodayColor else onSurfaceVariantColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)) else null
    val dayOfWeekTopLeft = Offset(lowPoint.x - dayOfWeekLayout.size.width / 2, size.height - 50.dp.toPx())
    drawText(dayOfWeekLayout, topLeft = dayOfWeekTopLeft)
    if (dotLayout != null) {
        val dotTopLeft = Offset(dayOfWeekTopLeft.x + dayOfWeekLayout.size.width, dayOfWeekTopLeft.y)
        drawText(dotLayout, topLeft = dotTopLeft)
    }

    val dayOfMonthText = forecast.date.dayOfMonth.toString()
    val dayOfMonthLayout = textMeasurer.measure(dayOfMonthText, style = TextStyle(color = onSurfaceVariantColor, fontSize = 14.sp))
    drawText(dayOfMonthLayout, topLeft = Offset(lowPoint.x - dayOfMonthLayout.size.width / 2, size.height - 32.dp.toPx()))

    val lineAlpha = 0.3f
    val lineStrokeWidth = 1.dp.toPx()
    val iconSize = 24.dp.toPx()
    val iconPadding = 4.dp.toPx()
    val textPadding = 1.dp.toPx()
    val yTopPadding = 15.dp.toPx()
    val highObstacleTop = highPoint.y - highTempLayout.size.height - 18.dp.toPx() - textPadding
    val highObstacleBottom = highPoint.y + iconSize / 2 + iconPadding
    val lowObstacleTop = lowPoint.y - iconSize / 2 - iconPadding
    val lowObstacleBottom = lowPoint.y + lowTempLayout.size.height + textPadding + 15.dp.toPx()
    val yBottom = size.height - yBottomPadding
    if (highObstacleTop > yTopPadding) {
        drawLine(
            color = OnSurfaceVariant.copy(alpha = lineAlpha),
            start = Offset(highPoint.x, yTopPadding),
            end = Offset(highPoint.x, highObstacleTop),
            strokeWidth = lineStrokeWidth
        )
    }
    if (lowObstacleTop > highObstacleBottom) {
        drawLine(
            color = OnSurfaceVariant.copy(alpha = lineAlpha),
            start = Offset(highPoint.x, highObstacleBottom),
            end = Offset(highPoint.x, lowObstacleTop),
            strokeWidth = lineStrokeWidth
        )
    }
    if (yBottom > lowObstacleBottom) {
        drawLine(
            color = OnSurfaceVariant.copy(alpha = lineAlpha),
            start = Offset(highPoint.x, lowObstacleBottom),
            end = Offset(highPoint.x, yBottom),
            strokeWidth = lineStrokeWidth
        )
    }
}

@DrawableRes
private fun mapConditionToPng(condition: WeatherCondition, isDay: Boolean): Int {
    return if (isDay) {
        when (condition) {
            WeatherCondition.DAY_SUNNY -> R.drawable.day
            WeatherCondition.DAY_PARTLY_CLOUDY -> R.drawable.cloudy_day
            WeatherCondition.DAY_CLOUDY -> R.drawable.cloudy
            WeatherCondition.DAY_RAIN_LIGHT -> R.drawable.light_rain_day
            WeatherCondition.DAY_RAIN_MEDIUM -> R.drawable.medium_rain_day
            WeatherCondition.DAY_RAIN_HEAVY -> R.drawable.heavy_rain
            WeatherCondition.DAY_SNOW -> R.drawable.snow_day
            WeatherCondition.DAY_FOG -> R.drawable.fog_day
            WeatherCondition.DAY_FOG_CLOUDY -> R.drawable.fog_cloudy
            WeatherCondition.DAY_THUNDERSTORM -> R.drawable.thunderstorm_day
            WeatherCondition.DAY_THUNDERSTORM_HEAVY -> R.drawable.thunderstorm_heavy_rain
            WeatherCondition.DAY_THUNDERSTORM_RAIN_LIGHT -> R.drawable.thunderstorm_light_rain_day
            WeatherCondition.DAY_THUNDERSTORM_RAIN_MEDIUM -> R.drawable.thunderstorm_medium_rain_day
            WeatherCondition.DAY_WIND -> R.drawable.wind_day
            WeatherCondition.DAY_WIND_CLOUDY -> R.drawable.wind_cloudy
            else -> R.drawable.cloudy
        }
    } else {
        when (condition) {
            WeatherCondition.NIGHT_CLEAR -> R.drawable.night
            WeatherCondition.NIGHT_PARTLY_CLOUDY -> R.drawable.cloudy_night
            WeatherCondition.NIGHT_CLOUDY -> R.drawable.cloudy_night
            WeatherCondition.NIGHT_RAIN_LIGHT -> R.drawable.light_rain_night
            WeatherCondition.NIGHT_RAIN_MEDIUM -> R.drawable.medium_rain_night
            WeatherCondition.NIGHT_SNOW -> R.drawable.snow_night
            WeatherCondition.NIGHT_FOG -> R.drawable.fog_night
            WeatherCondition.NIGHT_THUNDERSTORM -> R.drawable.thunderstorm_night
            WeatherCondition.NIGHT_THUNDERSTORM_RAIN_LIGHT -> R.drawable.thunderstorm_light_rain_night
            WeatherCondition.NIGHT_THUNDERSTORM_MEDIUM_RAIN -> R.drawable.thunderstorm_medium_rain_night
            WeatherCondition.NIGHT_WIND -> R.drawable.wind_night
            else -> R.drawable.cloudy_night
        }
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

private fun toFahrenheitAware(celsius: Int, unit: TemperatureUnit): Int = if (unit == TemperatureUnit.CELSIUS) celsius else toFahrenheit(celsius)

private fun createSmoothPath(points: List<ChartDataPoint>): Path {
    val path = Path()
    if (points.size < 2) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val midPointX = (p1.x + p2.x) / 2
        path.cubicTo(x1 = midPointX, y1 = p1.y, x2 = midPointX, y2 = p2.y, x3 = p2.x, y3 = p2.y)
    }
    return path
}

private fun DrawScope.drawAnimatedPath(
    path: Path,
    color: Color,
    progress: Float,
) {
    val animatedPath = Path()
    val measure = PathMeasure()
    measure.setPath(path, false)
    measure.getSegment(0f, measure.length * progress, animatedPath, true)

    drawPath(
        path = animatedPath,
        color = color,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawPathWithGaps(
    points: List<ChartDataPoint>,
    color: Color,
    progress: Float,
    gapRadius: Float
) {
    if (points.size < 2) return

    for (i in 0 until points.size - 1) {
        val startPoint = points[i]
        val endPoint = points[i + 1]

        val dx = endPoint.x - startPoint.x
        val dy = endPoint.y - startPoint.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

        if (distance == 0f) continue

        val unitX = dx / distance
        val unitY = dy / distance

        val adjustedStart = Offset(
            startPoint.x + unitX * gapRadius,
            startPoint.y
        )
        val adjustedEnd = Offset(
            endPoint.x - unitX * gapRadius,
            endPoint.y
        )

        val adjustedDistance = kotlin.math.sqrt(
            (adjustedEnd.x - adjustedStart.x) * (adjustedEnd.x - adjustedStart.x) +
                    (adjustedEnd.y - adjustedStart.y) * (adjustedEnd.y - adjustedStart.y)
        )

        if (adjustedDistance > 0f) {
            val segmentPath = Path().apply {
                moveTo(adjustedStart.x, adjustedStart.y)
                val midPointX = (adjustedStart.x + adjustedEnd.x) / 2
                cubicTo(
                    x1 = midPointX, y1 = adjustedStart.y,
                    x2 = midPointX, y2 = adjustedEnd.y,
                    x3 = adjustedEnd.x, y3 = adjustedEnd.y
                )
            }

            val segmentStartProgress = i.toFloat() / (points.size - 1)
            val segmentEndProgress = (i + 1).toFloat() / (points.size - 1)

            if (progress >= segmentEndProgress) {
                drawPath(
                    path = segmentPath,
                    color = color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            } else if (progress > segmentStartProgress) {
                val segmentProgress = (progress - segmentStartProgress) / (segmentEndProgress - segmentStartProgress)
                val animatedPath = Path()
                val measure = PathMeasure()
                measure.setPath(segmentPath, false)
                measure.getSegment(0f, measure.length * segmentProgress, animatedPath, true)

                drawPath(
                    path = animatedPath,
                    color = color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
