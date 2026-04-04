package com.example.modernweather.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

fun Context.localized(languageTag: String?): Context {
    if (languageTag.isNullOrBlank()) {
        val systemLocale = LocaleList.getAdjustedDefault().get(0) ?: Locale.getDefault()
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(systemLocale)
        configuration.setLocales(LocaleList(systemLocale))
        return createConfigurationContext(configuration)
    }

    val locale = Locale.forLanguageTag(languageTag)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLocales(LocaleList(locale))
    return createConfigurationContext(configuration)
}
