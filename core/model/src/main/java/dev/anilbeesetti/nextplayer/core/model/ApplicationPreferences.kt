package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ApplicationPreferences(
    val sortBy: SortBy = SortBy.TITLE,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val groupVideosByFolder: Boolean = true,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val useHighContrastDarkTheme: Boolean = false,
    val useDynamicColors: Boolean = true,
    val excludeFolders: List<String> = emptyList()
)
