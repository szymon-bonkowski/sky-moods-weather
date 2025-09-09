package com.example.modernweather.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modernweather.data.models.SunInfo
import com.example.modernweather.ui.screens.TitledCard
import com.example.modernweather.ui.viewmodel.WeatherViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SunCycle(sunInfo: SunInfo, viewModel: WeatherViewModel) {
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
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
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
