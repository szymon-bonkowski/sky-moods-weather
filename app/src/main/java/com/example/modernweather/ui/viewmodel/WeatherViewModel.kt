package com.example.modernweather.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.modernweather.data.models.Location
import com.example.modernweather.data.models.TemperatureUnit
import com.example.modernweather.data.models.UserSettings
import com.example.modernweather.data.models.WeatherData
import com.example.modernweather.data.repository.FakeWeatherRepository
import com.example.modernweather.data.repository.SettingsRepository
import com.example.modernweather.data.repository.WeatherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocationsUiState(
    val isLoading: Boolean = true,
    val locations: List<Location> = emptyList(),
    val error: String? = null
)

sealed interface WeatherDetailUiState {
    data object Loading : WeatherDetailUiState
    data class Success(val weatherData: WeatherData) : WeatherDetailUiState
    data class Error(val message: String) : WeatherDetailUiState
}

data class SettingsUiState(
    val userSettings: UserSettings = UserSettings(temperatureUnit = TemperatureUnit.CELSIUS)
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val weatherRepository: WeatherRepository = FakeWeatherRepository()
    private val settingsRepository: SettingsRepository = SettingsRepository(application)

    private val _locationsState = MutableStateFlow(LocationsUiState())
    val locationsState = _locationsState.asStateFlow()

    private val _weatherDetailState = MutableStateFlow<WeatherDetailUiState>(WeatherDetailUiState.Loading)
    val weatherDetailState = _weatherDetailState.asStateFlow()

    val settingsState: StateFlow<SettingsUiState> = settingsRepository.userSettingsFlow
        .map { SettingsUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    companion object {
        lateinit var Factory: ViewModelProvider.Factory
    }

    init {
        loadSavedLocations()
    }

    fun loadSavedLocations() {
        viewModelScope.launch {
            _locationsState.value = LocationsUiState(isLoading = true)
            weatherRepository.getSavedLocations()
                .catch { e ->
                    _locationsState.value = LocationsUiState(isLoading = false, error = e.message)
                }
                .collect { locations ->
                    _locationsState.value = LocationsUiState(isLoading = false, locations = locations)
                }
        }
    }

    fun loadWeatherData(locationId: String) {
        viewModelScope.launch {
            _weatherDetailState.value = WeatherDetailUiState.Loading
            weatherRepository.getWeatherData(locationId)
                .catch { e ->
                    _weatherDetailState.value = WeatherDetailUiState.Error("Nie udało się załadować danych: ${e.message}")
                }
                .collect { data ->
                    _weatherDetailState.value = WeatherDetailUiState.Success(data)
                }
        }
    }

    fun updateTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch {
            settingsRepository.updateTemperatureUnit(unit)
        }
    }
}
