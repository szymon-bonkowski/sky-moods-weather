package com.example.modernweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.modernweather.ui.navigation.AppNavigation
import com.example.modernweather.ui.theme.ModernWeatherTheme
import com.example.modernweather.ui.viewmodel.WeatherViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val viewModelFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return WeatherViewModel(application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }

        WeatherViewModel.Factory = viewModelFactory

        setContent {
            ModernWeatherTheme {
                AppNavigation()
            }
        }
    }
}
