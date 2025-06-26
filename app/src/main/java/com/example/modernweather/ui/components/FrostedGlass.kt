package com.example.modernweather.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.modernweather.ui.theme.FrostedGlassColor

@Composable
fun FrostedGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape,
    blur: Dp = 16.dp,
    backgroundColor: Color = FrostedGlassColor,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .applyBlur(radius = blur)
            .clip(shape)
            .background(backgroundColor)
    ) {
        content()
    }
}

fun Modifier.applyBlur(radius: Dp): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val androidEffect = android.graphics.RenderEffect.createBlurEffect(
            radius.value,
            radius.value,
            android.graphics.Shader.TileMode.DECAL
        )
        this.graphicsLayer(renderEffect = androidEffect.asComposeRenderEffect())
    } else {
        this
    }
}