package com.example.modernweather.utils

import android.content.Context
import com.example.modernweather.R

fun Context.locationNameForId(locationId: String, fallbackName: String): String {
    return when (locationId) {
        "warszawa" -> getString(R.string.location_name_warszawa)
        "krakow" -> getString(R.string.location_name_krakow)
        "gdansk" -> getString(R.string.location_name_gdansk)
        else -> fallbackName
    }
}
