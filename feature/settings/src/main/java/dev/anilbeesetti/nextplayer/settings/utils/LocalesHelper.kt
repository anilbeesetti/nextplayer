package dev.anilbeesetti.nextplayer.settings.utils

import dev.anilbeesetti.nextplayer.core.common.logging.NextLogger
import java.util.Locale

object LocalesHelper {

    fun getAvailableLocales(): List<Pair<String, String>> {
        return try {
            Locale.getAvailableLocales().map {
                val key = it.isO3Language
                val language = it.displayLanguage
                Pair(language, key)
            }.distinctBy { it.second }.sortedBy { it.first }
        } catch (e: Exception) {
            NextLogger.e("LocalesHelper", "Failed to list available locales", e)
            listOf()
        }
    }

    fun getLocaleDisplayLanguage(key: String): String {
        return try {
            Locale.getAvailableLocales().first { it.isO3Language == key }.displayLanguage
        } catch (e: Exception) {
            NextLogger.e("LocalesHelper", "Failed to get locale display name for key=$key", e)
            ""
        }
    }
}
