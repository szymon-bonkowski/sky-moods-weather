package com.example.modernweather.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.modernweather.data.models.TemperatureUnit
import com.example.modernweather.data.models.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        private val TEMP_UNIT_KEY = stringPreferencesKey("temperature_unit")
        private val IS_SYSTEM_THEME_KEY = booleanPreferencesKey("is_system_theme")
        private val IS_DARK_THEME_KEY = booleanPreferencesKey("is_dark_theme")
    }

    val userSettingsFlow: Flow<UserSettings> = dataStore.data.map { preferences ->
        val tempUnit = TemperatureUnit.valueOf(
            preferences[TEMP_UNIT_KEY] ?: TemperatureUnit.CELSIUS.name
        )
        val isSystemTheme = preferences[IS_SYSTEM_THEME_KEY] ?: true
        val isDarkTheme = preferences[IS_DARK_THEME_KEY] ?: false

        UserSettings(
            temperatureUnit = tempUnit,
            isSystemTheme = isSystemTheme,
            isDarkTheme = isDarkTheme
        )
    }

    suspend fun updateTemperatureUnit(temperatureUnit: TemperatureUnit) {
        dataStore.edit { preferences ->
            preferences[TEMP_UNIT_KEY] = temperatureUnit.name
        }
    }

    suspend fun updateTheme(isSystem: Boolean, isDark: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_SYSTEM_THEME_KEY] = isSystem
            preferences[IS_DARK_THEME_KEY] = isDark
        }
    }
}
