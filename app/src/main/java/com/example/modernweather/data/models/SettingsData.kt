package com.example.modernweather.data.models

import androidx.compose.runtime.Immutable
import com.example.modernweather.R

enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT
}

enum class AppLanguage(
    val labelResId: Int,
    val languageTag: String
) {
    SYSTEM(R.string.language_system_default, ""),
    POLISH(R.string.language_polish, "pl"),
    ENGLISH(R.string.language_english, "en"),
    TURKISH(R.string.language_turkish, "tr"),
    SPANISH(R.string.language_spanish, "es"),
    ITALIAN(R.string.language_italian, "it");

    companion object {
        fun fromLanguageTag(languageTag: String?): AppLanguage {
            return entries.firstOrNull { it.languageTag == languageTag } ?: SYSTEM
        }
    }
}

enum class WeatherDataSource {
    FAKE,
    OPEN_METEO
}

@Immutable
data class UserSettings(
    val temperatureUnit: TemperatureUnit,
    val isSystemTheme: Boolean,
    val isDarkTheme: Boolean,
    val weatherDataSource: WeatherDataSource = WeatherDataSource.FAKE,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM
)
