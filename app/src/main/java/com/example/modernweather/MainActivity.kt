package com.example.modernweather

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.modernweather.ui.navigation.AppNavigation
import com.example.modernweather.ui.theme.ModernWeatherTheme
import com.example.modernweather.ui.viewmodel.WeatherViewModel
import com.example.modernweather.data.models.AppLanguage
import com.example.modernweather.data.repository.SettingsRepository
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

        val appLanguage = runBlocking(Dispatchers.IO) {
            SettingsRepository(application).userSettingsFlow.first().appLanguage
        }
        AppCompatDelegate.setApplicationLocales(
            if (appLanguage == AppLanguage.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(appLanguage.languageTag)
            }
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory)
            val settingsState by viewModel.settingsState.collectAsState()

            val useDarkTheme = if (settingsState.isSystemTheme) {
                isSystemInDarkTheme()
            } else {
                settingsState.isDarkTheme
            }

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
