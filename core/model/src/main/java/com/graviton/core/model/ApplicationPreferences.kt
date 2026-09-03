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
    val musicPlayCounts: Map<String, Int> = emptyMap(),
    val musicLastPlayedAt: Map<String, Long> = emptyMap(),
    val musicListeningTimeMs: Map<String, Long> = emptyMap(),
    val musicFavorites: List<String> = emptyList(),
    val musicShowLyrics: Boolean = true,
    val musicRememberShuffle: Boolean = true,

    // Music player presentation. These remain in the existing application DataStore so upgrades
    // retain all previous preferences and no parallel settings store is introduced.
    val musicNowPlayingStyle: NowPlayingStyle = NowPlayingStyle.CLASSIC,
    val musicArtworkCornerRadius: Float = 28f,
    val musicArtworkSizePercent: Int = 92,
    val musicBackgroundStyle: MusicBackgroundStyle = MusicBackgroundStyle.THEME,
    val musicDynamicArtworkBackground: Boolean = true,
    val musicBlurIntensity: Float = 24f,
    val musicShowNextTrack: Boolean = true,
    val musicShowMetadata: Boolean = true,
    val musicShowCodecInfo: Boolean = false,
    val musicGestureControls: Boolean = true,
    val musicSeekGestureSensitivity: Float = 1f,
    val musicShowLyricsButton: Boolean = true,
    val musicShowQueueButton: Boolean = true,
    val musicShowSleepTimerButton: Boolean = true,
    val musicAnimationsEnabled: Boolean = true,
    val musicReplayGainEnabled: Boolean = false,
    val musicReplayGainPreampDb: Float = 0f,
    val musicEqualizerEnabled: Boolean = false,
    val musicEqualizerGainsDb: List<Float> = List(15) { 0f },
    val musicLyricsProviderPriority: List<LyricsSourceKind> = listOf(
        LyricsSourceKind.EMBEDDED,
        LyricsSourceKind.SIDECAR_LRC,
        LyricsSourceKind.SIDECAR_TTML,
        LyricsSourceKind.LRCLIB,
    ),

    // Restorable music queue. The service updates these compact values at safe transition points.
    val musicQueueUris: List<String> = emptyList(),
    val musicQueueIndex: Int = 0,
    val musicQueuePositionMs: Long = 0L,

    // Fields
    val showDurationField: Boolean = true,
    val showFolderDurationField: Boolean = true,
    val showExtensionField: Boolean = false,
    val showPathField: Boolean = true,
    val showResolutionField: Boolean = false,
    val showSizeField: Boolean = false,
    val showThumbnailField: Boolean = true,
    val showPlayedProgress: Boolean = true,

    // Video player tools. Bookmarks are keyed by media id so they follow the file, and the
    // tutorial flag is a plain boolean so the onboarding sheet can be dismissed permanently.
    val videoBookmarks: Map<String, List<VideoBookmark>> = emptyMap(),
    val playerTutorialShown: Boolean = false,

    // Thumbnail generation
    val thumbnailGenerationStrategy: ThumbnailGenerationStrategy = ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE,
    val thumbnailFramePosition: Float = DEFAULT_THUMBNAIL_FRAME_POSITION,
) {

    companion object {
        const val DEFAULT_THUMBNAIL_FRAME_POSITION = 0.33f
    }
}
