package com.example.modernweather.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.core.os.LocaleListCompat
import com.example.modernweather.data.models.Location
import com.example.modernweather.data.models.AppLanguage
import com.example.modernweather.data.models.TemperatureUnit
import com.example.modernweather.data.models.WeatherData
import com.example.modernweather.data.models.WeatherDataSource
import com.example.modernweather.data.repository.FakeWeatherRepository
import com.example.modernweather.data.repository.OpenMeteoWeatherRepository
import com.example.modernweather.data.repository.SettingsRepository
import com.example.modernweather.data.repository.WeatherRepository
import com.example.modernweather.nowcast.data.NowcastRepository
import com.example.modernweather.nowcast.model.NowcastAssessment
import com.example.modernweather.nowcast.worker.NowcastScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

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
    val nowcastUseTfliteEnabled: Boolean = true,
    val weatherDataSource: WeatherDataSource = WeatherDataSource.FAKE,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM
)

class WeatherViewModel(application: Application) : ViewModel() {

    companion object {
        lateinit var Factory: ViewModelProvider.Factory
    }

    private val settingsRepository = SettingsRepository(application)
    private val nowcastRepository = NowcastRepository(application)
    private val fakeWeatherRepository = FakeWeatherRepository(application)
    private val openMeteoWeatherRepository = OpenMeteoWeatherRepository(application)

    private var weatherRepository: WeatherRepository = fakeWeatherRepository
    private val applicationContext = application.applicationContext
    private var weatherDetailJob: Job? = null
    private var weatherDetailLocationId: String? = null

    private val weatherSourceJob = settingsRepository.userSettingsFlow
        .map { it.weatherDataSource }
        .distinctUntilChanged()
        .onEach { source ->
            selectWeatherRepository(source)
            if (weatherDetailLocationId != null) {
                reloadWeatherDetail()
            }
            loadSavedLocations()
        }
        .launchIn(viewModelScope)

    private val languageJob = settingsRepository.userSettingsFlow
        .map { it.appLanguage }
        .distinctUntilChanged()
        .onEach {
            if (weatherDetailLocationId != null) {
                reloadWeatherDetail()
            }
            loadSavedLocations()
        }
        .launchIn(viewModelScope)

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
            nowcastUseTfliteEnabled = nowcastSettings.useTfliteModel,
            weatherDataSource = userSettings.weatherDataSource,
            appLanguage = userSettings.appLanguage
        )
    }.stateIn(
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
            val languageTag = currentLanguageTag()
            _weatherDetailState.value = WeatherDetailUiState.Loading
            weatherRepository.getWeatherData(locationId, languageTag)
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

    fun updateWeatherDataSource(source: WeatherDataSource) {
        viewModelScope.launch {
            settingsRepository.updateWeatherDataSource(source)
        }
    }

    fun updateAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.updateAppLanguage(language)
            val locales = if (language == AppLanguage.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.languageTag)
            }
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
        }
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
            val languageTag = currentLanguageTag()
            _locationsState.value = LocationsUiState(isLoading = true)
            weatherRepository.getSavedLocations(languageTag)
                .catch { e ->
                    _locationsState.value = LocationsUiState(isLoading = false, error = e.message)
                }
                .collect { locations ->
                    _locationsState.value = LocationsUiState(isLoading = false, locations = locations)
                }
        }
    }

    fun setTestValues(currentTime: LocalTime, sunrise: LocalTime, sunset: LocalTime) {
        fakeWeatherRepository.setTestValues(currentTime, sunrise, sunset)
    }

    fun resetToRealTime() {
        fakeWeatherRepository.resetToRealTime()
    }

    fun getCurrentTime(): LocalTime {
        return weatherRepository.getCurrentTime()
    }

    private fun selectWeatherRepository(source: WeatherDataSource) {
        weatherRepository = when (source) {
            WeatherDataSource.FAKE -> fakeWeatherRepository
            WeatherDataSource.OPEN_METEO -> openMeteoWeatherRepository
        }
    }

    private fun reloadWeatherDetail() {
        val locationId = weatherDetailLocationId ?: return
        weatherDetailJob?.cancel()
        weatherDetailJob = null
        _weatherDetailState.value = WeatherDetailUiState.Loading
        loadWeatherData(locationId)
    }

    private fun currentLanguageTag(): String? {
        return settingsState.value.appLanguage.languageTag.takeIf { it.isNotBlank() }
    }
}
