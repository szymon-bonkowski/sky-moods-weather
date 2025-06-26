package com.example.modernweather.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modernweather.ui.theme.*

@Composable
fun AqiGauge(
    aqi: Int,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    verticalOffset: Dp = 10.dp
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val maxAqi = 300f
    val targetSweepAngle = (240f * (aqi / maxAqi)).coerceIn(0f, 240f)
    val animationDuration = 1500

    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    val sweepAngle by animateFloatAsState(
        targetValue = if (animationPlayed) targetSweepAngle else 0f,
        animationSpec = tween(durationMillis = animationDuration),
        label = "aqi_angle"
    )

    val animatedAqi by animateIntAsState(
        targetValue = if (animationPlayed) aqi else 0,
        animationSpec = tween(durationMillis = animationDuration),
        label = "aqi_number"
    )

    val currentAnimatedAqiValue = (sweepAngle / 240f) * maxAqi
    val gaugeColor = getAqiColor(aqiValue = currentAnimatedAqiValue)
    val aqiText = when {
        currentAnimatedAqiValue <= 50 -> "Dobra"
        currentAnimatedAqiValue <= 100 -> "Umiarkowana"
        currentAnimatedAqiValue <= 150 -> "Niezdrowa"
        currentAnimatedAqiValue <= 200 -> "Bardzo zła"
        else -> "Tragiczna"
    }

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(size)
            .offset(y = verticalOffset),
        contentAlignment = Alignment.Center
    ) {

        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)

            drawArc(
                color = surfaceVariant,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = gaugeColor,
                startAngle = 150f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = animatedAqi.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = aqiText,
                fontSize = 12.sp,
                color = gaugeColor,
            )
        }
    }
}

private fun getAqiColor(aqiValue: Float): Color {
    return when {
        aqiValue <= 50f -> AqiGood
        aqiValue <= 100f -> {
            val fraction = (aqiValue - 50f) / 50f
            lerp(AqiGood, AqiModerate, fraction)
        }
        aqiValue <= 150f -> {
            val fraction = (aqiValue - 100f) / 50f
            lerp(AqiModerate, AqiUnhealthy, fraction)
        }
        aqiValue <= 200f -> {
            val fraction = (aqiValue - 150f) / 50f
            lerp(AqiUnhealthy, AqiVeryUnhealthy, fraction)
        }
        else -> AqiVeryUnhealthy
    }
}