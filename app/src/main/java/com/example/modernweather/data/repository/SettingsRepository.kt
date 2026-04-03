package com.example.modernweather.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.modernweather.data.models.AppLanguage
import com.example.modernweather.data.models.TemperatureUnit
import com.example.modernweather.data.models.UserSettings
import com.example.modernweather.data.models.WeatherDataSource
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        private val TEMP_UNIT_KEY = stringPreferencesKey("temperature_unit")
        private val IS_SYSTEM_THEME_KEY = booleanPreferencesKey("is_system_theme")
        private val IS_DARK_THEME_KEY = booleanPreferencesKey("is_dark_theme")
        private val WEATHER_SOURCE_KEY = intPreferencesKey("weather_source")
        private val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")
    }

    // Use distinctUntilChanged to prevent unnecessary recompositions and I/O
    val userSettingsFlow: Flow<UserSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
        val tempUnit = TemperatureUnit.entries.firstOrNull {
            it.name == preferences[TEMP_UNIT_KEY]
        } ?: TemperatureUnit.CELSIUS
        val isSystemTheme = preferences[IS_SYSTEM_THEME_KEY] ?: true
        val isDarkTheme = preferences[IS_DARK_THEME_KEY] ?: false
        val weatherDataSource = WeatherDataSource.entries.getOrNull(
            preferences[WEATHER_SOURCE_KEY] ?: WeatherDataSource.FAKE.ordinal
        ) ?: WeatherDataSource.FAKE
        val appLanguage = AppLanguage.fromLanguageTag(preferences[APP_LANGUAGE_KEY])

        UserSettings(
            temperatureUnit = tempUnit,
            isSystemTheme = isSystemTheme,
            isDarkTheme = isDarkTheme,
            weatherDataSource = weatherDataSource,
            appLanguage = appLanguage
        )
    }
        .distinctUntilChanged()

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

    suspend fun updateWeatherDataSource(source: WeatherDataSource) {
        dataStore.edit { preferences ->
            preferences[WEATHER_SOURCE_KEY] = source.ordinal
        }
    }

    suspend fun updateAppLanguage(language: AppLanguage) {
        dataStore.edit { preferences ->
            preferences[APP_LANGUAGE_KEY] = language.languageTag
        }
    }
}
