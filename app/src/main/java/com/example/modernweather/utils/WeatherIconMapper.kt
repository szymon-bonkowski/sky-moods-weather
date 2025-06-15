package com.example.modernweather.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.modernweather.data.models.WeatherCondition

object WeatherIconMapper {
    @Composable
    fun getIcon(condition: WeatherCondition): ImageVector {
        return when (condition) {
            WeatherCondition.SUNNY -> Icons.Default.WbSunny
            WeatherCondition.PARTLY_CLOUDY -> Icons.Default.WbCloudy
            WeatherCondition.CLOUDY -> Icons.Default.Cloud
            WeatherCondition.RAIN -> Icons.Outlined.WaterDrop
            WeatherCondition.HEAVY_RAIN -> Icons.Default.Grain
            WeatherCondition.THUNDERSTORM -> Icons.Default.Thunderstorm
            WeatherCondition.SNOW -> Icons.Default.AcUnit
            WeatherCondition.FOG -> Icons.Default.Cloud
        }
    }
}
