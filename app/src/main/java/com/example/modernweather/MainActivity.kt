package com.example.modernweather

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modernweather.ui.navigation.AppNavigation
import com.example.modernweather.ui.theme.ModernWeatherTheme
import com.example.modernweather.ui.viewmodel.WeatherViewModel
import com.example.modernweather.data.models.AppLanguage
import com.example.modernweather.data.repository.SettingsRepository
import com.example.modernweather.utils.localized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
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

        WeatherViewModel.Factory = viewModelFactory

        val savedAppLanguage = runBlocking(Dispatchers.IO) {
            SettingsRepository(application).userSettingsFlow.first().appLanguage
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory)
            val settingsState by viewModel.settingsState.collectAsState()
            var appLanguage by remember { mutableStateOf(savedAppLanguage) }
            var hasHandledInitialLanguage by remember { mutableStateOf(false) }

            LaunchedEffect(settingsState.appLanguage) {
                if (!hasHandledInitialLanguage) {
                    hasHandledInitialLanguage = true
                    appLanguage = when {
                        settingsState.appLanguage != AppLanguage.SYSTEM -> settingsState.appLanguage
                        savedAppLanguage == AppLanguage.SYSTEM -> AppLanguage.SYSTEM
                        else -> savedAppLanguage
                    }
                } else if (settingsState.appLanguage != appLanguage) {
                    appLanguage = settingsState.appLanguage
                }
            }

            val baseContext = LocalContext.current
            val localizedContext = remember(appLanguage, baseContext) {
                baseContext.localized(appLanguage.languageTag.takeIf { it.isNotBlank() })
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
                        AppNavigation()
                    }
                }
            }
        }
    }
}
