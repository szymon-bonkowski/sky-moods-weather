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
import com.example.modernweather.data.models.TemperatureUnit
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
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
            TitledCard(title = "JEDNOSTKI") {
                TemperatureUnitSelector(
                    selectedUnit = settingsState.temperatureUnit,
                    onUnitSelected = { viewModel.updateTemperatureUnit(it) }
                )
            }

            TitledCard(title = "WYGLĄD") {
                ThemeSelector(
                    isSystem = settingsState.isSystemTheme,
                    isDark = settingsState.isDarkTheme,
                    onThemeSelected = { isSystem, isDark ->
                        viewModel.updateTheme(isSystem, isDark)
                    }
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
    SettingItem(label = "Temperatura") {
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
        SettingItem(label = "Motyw systemowy") {
            Switch(checked = isSystem, onCheckedChange = { onThemeSelected(it, isDark) })
        }
        AnimatedVisibility(visible = !isSystem) {
            SettingItem(label = "Tryb ciemny") {
                Switch(checked = isDark, onCheckedChange = { onThemeSelected(isSystem, it) })
            }
        }
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
