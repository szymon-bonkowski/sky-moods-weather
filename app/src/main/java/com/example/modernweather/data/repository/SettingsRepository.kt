package com.example.modernweather.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    }

    val userSettingsFlow: Flow<UserSettings> = dataStore.data.map { preferences ->
        val tempUnit = TemperatureUnit.valueOf(
            preferences[TEMP_UNIT_KEY] ?: TemperatureUnit.CELSIUS.name
        )
        UserSettings(temperatureUnit = tempUnit)
    }

    suspend fun updateTemperatureUnit(temperatureUnit: TemperatureUnit) {
        dataStore.edit { preferences ->
            preferences[TEMP_UNIT_KEY] = temperatureUnit.name
        }
    }
}

