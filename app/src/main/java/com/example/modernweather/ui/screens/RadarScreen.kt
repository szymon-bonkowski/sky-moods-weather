package com.example.modernweather.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.modernweather.R
import com.example.modernweather.ui.components.FrostedGlassBox
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(onNavigateBack: () -> Unit) {
    var isPlaying by remember { mutableStateOf(true) }
    var timelineValue by remember { mutableFloatStateOf(1f) }
    val animatedTimelineValue by animateFloatAsState(
        targetValue = timelineValue,
        animationSpec = tween(durationMillis = 300), label = "timeline_anim"
    )

}