package com.example.modernweather.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.modernweather.data.models.*
import com.example.modernweather.data.repository.FakeWeatherRepository
import com.example.modernweather.data.repository.SettingsRepository
import com.example.modernweather.data.repository.WeatherRepository
import com.example.modernweather.nowcast.data.NowcastRepository
import com.example.modernweather.nowcast.model.NowcastAssessment
import com.example.modernweather.nowcast.worker.NowcastScheduler
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
    val isDarkTheme: Boolean = false,
    val nowcastMonitoringEnabled: Boolean = true,
    val nowcastNotificationsEnabled: Boolean = true,
    val nowcastUseTfliteEnabled: Boolean = true
)

class WeatherViewModel(application: Application) : ViewModel() {

    companion object {
        lateinit var Factory: ViewModelProvider.Factory
    }

    private val weatherRepository: WeatherRepository = FakeWeatherRepository()
    private val settingsRepository: SettingsRepository = SettingsRepository(application)
    private val nowcastRepository: NowcastRepository = NowcastRepository(application)
    private val applicationContext = application.applicationContext
    private var weatherDetailJob: Job? = null
    private var weatherDetailLocationId: String? = null

    private val _locationsState = MutableStateFlow(LocationsUiState())
    val locationsState = _locationsState.asStateFlow()

    private val _weatherDetailState = MutableStateFlow<WeatherDetailUiState>(WeatherDetailUiState.Loading)
    val weatherDetailState = _weatherDetailState.asStateFlow()

    val settingsState: StateFlow<SettingsUiState> = combine(
        settingsRepository.userSettingsFlow,
        nowcastRepository.settingsFlow
    ) { userSettings, nowcastSettings ->
        SettingsUiState(
            temperatureUnit = userSettings.temperatureUnit,
            isSystemTheme = userSettings.isSystemTheme,
            isDarkTheme = userSettings.isDarkTheme,
            nowcastMonitoringEnabled = nowcastSettings.monitoringEnabled,
            nowcastNotificationsEnabled = nowcastSettings.notificationsEnabled,
            nowcastUseTfliteEnabled = nowcastSettings.useTfliteModel
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    val nowcastAssessmentState: StateFlow<NowcastAssessment> = nowcastRepository.assessmentFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NowcastAssessment()
        )

    init {
        loadSavedLocations()
        viewModelScope.launch {
            val nowcastSettings = nowcastRepository.getSettings()
            if (nowcastSettings.monitoringEnabled) {
                NowcastScheduler.schedule(
                    context = applicationContext,
                    intervalMinutes = nowcastSettings.sampleIntervalMinutes
                )
            } else {
                NowcastScheduler.cancel(applicationContext)
            }
        }
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

    fun updateNowcastMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            nowcastRepository.updateMonitoringEnabled(enabled)
            val current = nowcastRepository.getSettings()
            if (enabled) {
                NowcastScheduler.schedule(
                    context = applicationContext,
                    intervalMinutes = current.sampleIntervalMinutes,
                    immediate = true
                )
            } else {
                NowcastScheduler.cancel(applicationContext)
            }
        }
    }

    fun updateNowcastNotifications(enabled: Boolean) {
        viewModelScope.launch {
            nowcastRepository.updateNotificationsEnabled(enabled)
        }
    }

    fun updateNowcastUseTflite(enabled: Boolean) {
        viewModelScope.launch {
            nowcastRepository.updateUseTflite(enabled)
        }
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
