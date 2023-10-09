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
    val excludeFolders: List<String> = emptyList(),

    // View preferences
    val thumbnailSize: Size = Size.LARGE,

    // Fields
    val showDurationField: Boolean = true,
    val showExtensionField: Boolean = false,
    val showPathField: Boolean = true,
    val showResolutionField: Boolean = false,
    val showSizeField: Boolean = false,
    val showThumbnailField: Boolean = true,
    val showVideosInGrid: Boolean = false
)

enum class Size {
    COMPACT, MEDIUM, LARGE
}
