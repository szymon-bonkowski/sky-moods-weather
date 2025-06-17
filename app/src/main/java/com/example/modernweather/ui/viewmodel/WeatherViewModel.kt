package com.example.modernweather.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.modernweather.data.models.*
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
    data class Success(val weatherData: WeatherData, val aiInsight: String) : WeatherDetailUiState
    data class Error(val message: String) : WeatherDetailUiState
}

data class SettingsUiState(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val isSystemTheme: Boolean = true,
    val isDarkTheme: Boolean = false
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        lateinit var Factory: ViewModelProvider.Factory
    }

    private val weatherRepository: WeatherRepository = FakeWeatherRepository()
    private val settingsRepository: SettingsRepository = SettingsRepository(application)

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
                    val insight = generateAiInsight(data)
                    _weatherDetailState.value = WeatherDetailUiState.Success(data, insight)
                }
        }
    }

    fun updateTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { settingsRepository.updateTemperatureUnit(unit) }
    }

    fun updateTheme(isSystem: Boolean, isDark: Boolean) {
        viewModelScope.launch { settingsRepository.updateTheme(isSystem, isDark) }
    }

    private fun generateAiInsight(data: WeatherData): String {
        val temp = data.currentWeather.temperature
        return when (data.currentWeather.conditionEnum) {
            WeatherCondition.SUNNY -> "Czyste niebo i słońce przez cały dzień. Idealne warunki na aktywność na zewnątrz. Pamiętaj o kremie z filtrem, indeks UV może być wysoki."
            WeatherCondition.THUNDERSTORM -> "Uwaga! Spodziewane są gwałtowne burze z silnym wiatrem. Unikaj otwartych przestrzeni i zabezpiecz luźne przedmioty na balkonie."
            WeatherCondition.RAIN -> "Spodziewaj się przelotnych opadów deszczu. Warto zabrać ze sobą parasol lub kurtkę przeciwdeszczową. Drogi mogą być śliskie."
            WeatherCondition.SNOW -> "Zimowa aura w pełni. Opady śniegu mogą powodować utrudnienia w ruchu. Ubierz się ciepło i zachowaj ostrożność na chodnikach."
            else -> "Pogoda będzie zmienna. Aktualnie temperatura wynosi $temp°C. W ciągu dnia możliwe są zarówno chwile ze słońcem, jak i przelotne opady. Bądź gotowy na wszystko."
        }
    }
}
