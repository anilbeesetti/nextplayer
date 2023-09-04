package dev.anilbeesetti.nextplayer.settings.utils

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
            e.printStackTrace()
            listOf()
        }
    }

    fun getLocaleDisplayLanguage(key: String): String {
        return try {
            Locale.getAvailableLocales().first { it.isO3Language == key }.displayLanguage
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
