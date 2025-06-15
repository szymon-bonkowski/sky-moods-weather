package com.example.modernweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.modernweather.ui.navigation.AppNavigation
import com.example.modernweather.ui.theme.ModernWeatherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ModernWeatherTheme {
                AppNavigation()
            }
        }
    }
}

