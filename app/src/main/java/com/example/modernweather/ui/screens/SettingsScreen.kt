package com.example.modernweather.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.modernweather.R
import com.example.modernweather.data.models.AppLanguage
import com.example.modernweather.data.models.TemperatureUnit
import com.example.modernweather.data.models.WeatherDataSource
import com.example.modernweather.ui.viewmodel.WeatherViewModel

@Composable
fun TitledCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit
) {
    val settingsState by viewModel.settingsState.collectAsState()
    val context = LocalContext.current
    val hasBarometer = remember {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TitledCard(title = stringResource(R.string.settings_section_units)) {
                TemperatureUnitSelector(
                    selectedUnit = settingsState.temperatureUnit,
                    onUnitSelected = { viewModel.updateTemperatureUnit(it) }
                )
            }

            TitledCard(title = stringResource(R.string.settings_section_appearance)) {
                ThemeSelector(
                    isSystem = settingsState.isSystemTheme,
                    isDark = settingsState.isDarkTheme,
                    onThemeSelected = { isSystem, isDark ->
                        viewModel.updateTheme(isSystem, isDark)
                    }
                )
            }

            TitledCard(title = stringResource(R.string.settings_section_data_source)) {
                WeatherSourceSelector(
                    selectedSource = settingsState.weatherDataSource,
                    onSourceSelected = viewModel::updateWeatherDataSource
                )
            }

            TitledCard(title = stringResource(R.string.settings_section_nowcast)) {
                NowcastSettingsSection(
                    monitoringEnabled = settingsState.nowcastMonitoringEnabled,
                    notificationsEnabled = settingsState.nowcastNotificationsEnabled,
                    useTflite = settingsState.nowcastUseTfliteEnabled,
                    hasBarometer = hasBarometer,
                    onMonitoringChanged = viewModel::updateNowcastMonitoring,
                    onNotificationsChanged = viewModel::updateNowcastNotifications,
                    onUseTfliteChanged = viewModel::updateNowcastUseTflite
                )
            }

            TitledCard(title = stringResource(R.string.settings_section_language)) {
                LanguageSelector(
                    selectedLanguage = settingsState.appLanguage,
                    onLanguageSelected = viewModel::updateAppLanguage
                )
            }
        }
    }
}

@Composable
fun TemperatureUnitSelector(
    selectedUnit: TemperatureUnit,
    onUnitSelected: (TemperatureUnit) -> Unit
) {
    SettingItem(label = stringResource(R.string.settings_temperature_label)) {
        SegmentedButtonRow(
            selectedUnit = selectedUnit,
            onUnitSelected = onUnitSelected
        )
    }
}

@Composable
fun ThemeSelector(
    isSystem: Boolean,
    isDark: Boolean,
    onThemeSelected: (Boolean, Boolean) -> Unit
) {
    Column {
        SettingItem(label = stringResource(R.string.settings_system_theme_label)) {
            Switch(checked = isSystem, onCheckedChange = { onThemeSelected(it, isDark) })
        }
        AnimatedVisibility(visible = !isSystem) {
            SettingItem(label = stringResource(R.string.settings_dark_theme_label)) {
                Switch(checked = isDark, onCheckedChange = { onThemeSelected(isSystem, it) })
            }
        }
    }
}

@Composable
fun WeatherSourceSelector(
    selectedSource: WeatherDataSource,
    onSourceSelected: (WeatherDataSource) -> Unit
) {
    Column {
        SettingItem(label = stringResource(R.string.settings_use_open_meteo)) {
            Switch(
                checked = selectedSource == WeatherDataSource.OPEN_METEO,
                onCheckedChange = { checked ->
                    onSourceSelected(if (checked) WeatherDataSource.OPEN_METEO else WeatherDataSource.FAKE)
                }
            )
        }
        Text(
            text = if (selectedSource == WeatherDataSource.OPEN_METEO) {
                stringResource(R.string.settings_open_meteo_description)
            } else {
                stringResource(R.string.settings_fake_data_description)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SettingItem(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@Composable
private fun SegmentedButtonRow(
    selectedUnit: TemperatureUnit,
    onUnitSelected: (TemperatureUnit) -> Unit
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        Button(
            onClick = { onUnitSelected(TemperatureUnit.CELSIUS) },
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedUnit == TemperatureUnit.CELSIUS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selectedUnit == TemperatureUnit.CELSIUS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("°C")
        }
        Button(
            onClick = { onUnitSelected(TemperatureUnit.FAHRENHEIT) },
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedUnit == TemperatureUnit.FAHRENHEIT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selectedUnit == TemperatureUnit.FAHRENHEIT) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("°F")
        }
    }
}

@Composable
private fun NowcastSettingsSection(
    monitoringEnabled: Boolean,
    notificationsEnabled: Boolean,
    useTflite: Boolean,
    hasBarometer: Boolean,
    onMonitoringChanged: (Boolean) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onUseTfliteChanged: (Boolean) -> Unit
) {
    Column {
        SettingItem(label = stringResource(R.string.settings_enable_local_nowcast)) {
            Switch(
                checked = monitoringEnabled && hasBarometer,
                onCheckedChange = onMonitoringChanged,
                enabled = hasBarometer
            )
        }

        if (!hasBarometer) {
            Text(
                text = stringResource(R.string.settings_no_barometer_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        AnimatedVisibility(visible = monitoringEnabled && hasBarometer) {
            Column {
                SettingItem(label = stringResource(R.string.settings_nowcast_notifications_label)) {
                    Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsChanged)
                }
                SettingItem(label = stringResource(R.string.settings_nowcast_ml_model_label)) {
                    Switch(checked = useTflite, onCheckedChange = onUseTfliteChanged)
                }
            }
        }
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { language ->
            LanguageOption(
                language = language,
                selected = language == selectedLanguage,
                onClick = { onLanguageSelected(language) }
            )
        }
    }
}

@Composable
private fun LanguageOption(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stringResource(language.labelResId), style = MaterialTheme.typography.bodyLarge)
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null)
        }
    }
}
