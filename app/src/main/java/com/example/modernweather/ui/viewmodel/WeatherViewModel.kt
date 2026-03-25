package com.example.modernweather.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.modernweather.data.models.*
import com.example.modernweather.data.repository.FakeWeatherRepository
import com.example.modernweather.data.repository.SettingsRepository
import com.example.modernweather.data.repository.WeatherRepository
import kotlinx.coroutines.Job
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
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val isSystemTheme: Boolean = true,
    val isDarkTheme: Boolean = false
)

class WeatherViewModel(application: Application) : ViewModel() {

    companion object {
        lateinit var Factory: ViewModelProvider.Factory
    }

    private val weatherRepository: WeatherRepository = FakeWeatherRepository()
    private val settingsRepository: SettingsRepository = SettingsRepository(application)
    private var weatherDetailJob: Job? = null
    private var weatherDetailLocationId: String? = null

    private val _locationsState = MutableStateFlow(LocationsUiState())
    val locationsState = _locationsState.asStateFlow()

    private val _weatherDetailState = MutableStateFlow<WeatherDetailUiState>(WeatherDetailUiState.Loading)
    val weatherDetailState = _weatherDetailState.asStateFlow()

    val settingsState: StateFlow<SettingsUiState> = settingsRepository.userSettingsFlow
        .map {
            SettingsUiState(
                temperatureUnit = it.temperatureUnit,
                isSystemTheme = it.isSystemTheme,
                isDarkTheme = it.isDarkTheme
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    init {
        loadSavedLocations()
    }

    fun loadWeatherData(locationId: String) {
        val currentState = _weatherDetailState.value
        if (currentState is WeatherDetailUiState.Success && currentState.weatherData.location.id == locationId) {
            return
        }

        if (weatherDetailLocationId == locationId && weatherDetailJob?.isActive == true) {
            return
        }

        weatherDetailJob?.cancel()
        weatherDetailLocationId = locationId

        weatherDetailJob = viewModelScope.launch {
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
        viewModelScope.launch { settingsRepository.updateTemperatureUnit(unit) }
    }

    fun updateTheme(isSystem: Boolean, isDark: Boolean) {
        viewModelScope.launch { settingsRepository.updateTheme(isSystem, isDark) }
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

    fun setTestValues(currentTime: java.time.LocalTime, sunrise: java.time.LocalTime, sunset: java.time.LocalTime) {
        (weatherRepository as? FakeWeatherRepository)?.setTestValues(currentTime, sunrise, sunset)
    }

    fun resetToRealTime() {
        (weatherRepository as? FakeWeatherRepository)?.resetToRealTime()
    }

    fun getCurrentTime(): java.time.LocalTime {
        return java.time.LocalTime.now()
    }
}
