package com.graviton.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ApplicationPreferences(
    val sortBy: Sort.By = Sort.By.TITLE,
    val sortOrder: Sort.Order = Sort.Order.ASCENDING,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    val useHighContrastDarkTheme: Boolean = false,
    val useDynamicColors: Boolean = true,
    val markLastPlayedMedia: Boolean = true,
    val excludeFolders: List<String> = emptyList(),
    val mediaViewMode: MediaViewMode = MediaViewMode.FOLDERS,
    val mediaLayoutMode: MediaLayoutMode = MediaLayoutMode.LIST,

    // Navigation bar
    val showBottomNavigation: Boolean = true,
    val showPlaylistsTab: Boolean = true,
    val showNetworkTab: Boolean = false,
    val showMusicTab: Boolean = true,

    // Music library playback history (Booming-style resume / recently played)
    val musicRecentlyPlayedUris: List<String> = emptyList(),
    val musicFolderLastUri: Map<String, String> = emptyMap(),
    val musicShowLyrics: Boolean = true,
    val musicRememberShuffle: Boolean = true,

    // Fields
    val showDurationField: Boolean = true,
    val showFolderDurationField: Boolean = true,
    val showExtensionField: Boolean = false,
    val showPathField: Boolean = true,
    val showResolutionField: Boolean = false,
    val showSizeField: Boolean = false,
    val showThumbnailField: Boolean = true,
    val showPlayedProgress: Boolean = true,

    // Thumbnail generation
    val thumbnailGenerationStrategy: ThumbnailGenerationStrategy = ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE,
    val thumbnailFramePosition: Float = DEFAULT_THUMBNAIL_FRAME_POSITION,
) {

    companion object {
        const val DEFAULT_THUMBNAIL_FRAME_POSITION = 0.33f
    }
}
