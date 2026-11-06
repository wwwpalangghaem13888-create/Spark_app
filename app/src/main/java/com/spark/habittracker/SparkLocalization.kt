package com.spark.habittracker

import androidx.compose.runtime.compositionLocalOf

/** App-wide language used by Compose screens that opt into localized labels. */
val LocalSparkLanguage = compositionLocalOf { "fa" }

internal fun localizedText(language: String, persian: String, english: String, arabic: String): String =
    when (language.lowercase()) {
        "en" -> english
        "ar" -> arabic
        else -> persian
    }
