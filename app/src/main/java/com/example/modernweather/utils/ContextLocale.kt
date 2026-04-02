package com.example.modernweather.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

fun Context.localized(languageTag: String?): Context {
    if (languageTag.isNullOrBlank()) return this

    val locale = Locale.forLanguageTag(languageTag)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLocales(LocaleList(locale))
    return createConfigurationContext(configuration)
}
