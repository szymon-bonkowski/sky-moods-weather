package com.example.modernweather

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modernweather.ui.navigation.AppNavigation
import com.example.modernweather.ui.theme.ModernWeatherTheme
import com.example.modernweather.ui.viewmodel.WeatherViewModel
import com.example.modernweather.utils.localized

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

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

        setContent {
            val viewModel: WeatherViewModel = viewModel(factory = viewModelFactory)
            val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
            val appLanguage = settingsState.appLanguage

            val baseContext = LocalContext.current
            val localizedContext = remember(appLanguage, baseContext) {
                baseContext.localized(appLanguage.languageTag)
            }

            val useDarkTheme = if (settingsState.isSystemTheme) {
                isSystemInDarkTheme()
            } else {
                settingsState.isDarkTheme
            }

            CompositionLocalProvider(LocalContext provides localizedContext) {
                ModernWeatherTheme(darkTheme = useDarkTheme) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AppNavigation(
                            weatherViewModel = viewModel,
                            onRequestNotificationPermission = ::requestNowcastNotificationPermission
                        )
                    }
                }
            }
        }
    }

    private fun requestNowcastNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
