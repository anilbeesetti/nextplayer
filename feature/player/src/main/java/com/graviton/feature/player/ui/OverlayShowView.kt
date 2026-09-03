package com.graviton.feature.player.ui

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.graviton.core.model.DecoderMode
import com.graviton.core.model.MediaChapter
import com.graviton.core.model.MediaInfo
import com.graviton.core.model.PlayerPreferences
import com.graviton.core.model.ScreenOrientation
import com.graviton.core.model.VideoBookmark
import com.graviton.core.model.VideoContentScale
import com.graviton.feature.player.decoder.PlaybackDiagnosticsSnapshot
import com.graviton.feature.player.extensions.noRippleClickable
import com.graviton.feature.player.state.CutSegmentState
import com.graviton.feature.player.state.MediaPresentationState
import com.graviton.feature.player.state.SubtitleOptionsEvent
import com.graviton.feature.player.state.SubtitleOptionsState
import com.graviton.feature.player.state.TracksState
import com.graviton.feature.player.state.VideoZoomAndContentScaleState
import com.graviton.feature.player.ui.sheets.BookmarksSheet
import com.graviton.feature.player.ui.sheets.ChaptersSheet
import com.graviton.feature.player.ui.sheets.CutSegmentSheet
import com.graviton.feature.player.ui.sheets.DisplaySettingsSheet
import com.graviton.feature.player.ui.sheets.MoreOptionsSheet
import com.graviton.feature.player.ui.sheets.TutorialSheet
import com.graviton.feature.player.ui.sheets.VideoInformationSheet

/**
 * Fans the single [OverlayView] selection out to the sheet that owns it.
 *
 * Exactly one sheet can be open at a time, which is what keeps the video visible and prevents two
 * sheets from disagreeing about the same player state.
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.OverlayShowView(
    player: Player,
    overlayView: OverlayView?,
    playerPreferences: PlayerPreferences,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    subtitleTracksState: TracksState,
    subtitleOptionsState: SubtitleOptionsState,
    cutSegmentState: CutSegmentState,
    mediaPresentationState: MediaPresentationState,
    diagnostics: PlaybackDiagnosticsSnapshot,
    bookmarks: List<VideoBookmark>,
    chapters: List<MediaChapter>,
    mediaInfo: MediaInfo?,
    isLoadingDetails: Boolean,
    isPipSupported: Boolean,
    aspectRatioLabel: String,
    longPressSpeedLabel: String,
    bookmarkCountLabel: String,
    chapterCountLabel: String,
    cutSegmentLabel: String,
    onDismiss: () -> Unit = {},
    onOverlayViewChange: (OverlayView) -> Unit = {},
    onSelectSubtitleClick: () -> Unit = {},
    onSubtitleOptionEvent: (SubtitleOptionsEvent) -> Unit = {},
    onVideoContentScaleChanged: (VideoContentScale) -> Unit = {},
    onDecoderModeSelected: (DecoderMode) -> Unit = {},
    onScreenOrientationChanged: (ScreenOrientation) -> Unit = {},
    onPanGestureChanged: (Boolean) -> Unit = {},
    onAutoPipChanged: (Boolean) -> Unit = {},
    onControllerAutoHideTimeoutChanged: (Int) -> Unit = {},
    onGesturesClick: () -> Unit = {},
    onNetworkStreamClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAddBookmark: (label: String) -> Unit = {},
    onDeleteBookmark: (VideoBookmark) -> Unit = {},
    onTutorialDontShowAgain: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .then(
                if (overlayView != null) {
                    Modifier.noRippleClickable(onClick = onDismiss)
                } else {
                    Modifier
                },
            ),
    )

    AudioTrackSelectorView(
        show = overlayView == OverlayView.AUDIO_SELECTOR,
        player = player,
        onDismiss = onDismiss,
    )

    SubtitleSelectorView(
        show = overlayView == OverlayView.SUBTITLE_SELECTOR,
        player = player,
        onSelectSubtitleClick = onSelectSubtitleClick,
        onEvent = onSubtitleOptionEvent,
        onDismiss = onDismiss,
    )

    PlaybackSpeedSelectorView(
        show = overlayView == OverlayView.PLAYBACK_SPEED,
        player = player,
    )

    VideoContentScaleSelectorView(
        show = overlayView == OverlayView.VIDEO_CONTENT_SCALE,
        videoContentScale = videoZoomAndContentScaleState.videoContentScale,
        onVideoContentScaleChanged = onVideoContentScaleChanged,
        onDismiss = onDismiss,
    )

    DecoderSelectorView(
        show = overlayView == OverlayView.DECODER_SELECTOR,
        currentDecoderMode = playerPreferences.decoderMode,
        onDecoderModeSelected = onDecoderModeSelected,
        onDismiss = onDismiss,
    )

    PlaylistView(
        show = overlayView == OverlayView.PLAYLIST,
        player = player,
    )

    MoreOptionsSheet(
        show = overlayView == OverlayView.MORE_OPTIONS,
        aspectRatioLabel = aspectRatioLabel,
        longPressSpeedLabel = longPressSpeedLabel,
        bookmarkCountLabel = bookmarkCountLabel,
        chapterCountLabel = chapterCountLabel,
        cutSegmentLabel = cutSegmentLabel,
        onAspectRatioClick = { onOverlayViewChange(OverlayView.VIDEO_CONTENT_SCALE) },
        onLongPressSpeedClick = { onOverlayViewChange(OverlayView.PLAYBACK_SPEED) },
        onDisplaySettingsClick = { onOverlayViewChange(OverlayView.DISPLAY_SETTINGS) },
        onPlaylistClick = { onOverlayViewChange(OverlayView.PLAYLIST) },
        onNetworkStreamClick = onNetworkStreamClick,
        onInformationClick = { onOverlayViewChange(OverlayView.VIDEO_INFORMATION) },
        onBookmarkClick = { onOverlayViewChange(OverlayView.BOOKMARKS) },
        onChapterClick = { onOverlayViewChange(OverlayView.CHAPTERS) },
        onCutClick = { onOverlayViewChange(OverlayView.CUT_SEGMENT) },
        onTutorialClick = { onOverlayViewChange(OverlayView.TUTORIAL) },
        onShareClick = onShareClick,
        onSettingsClick = onSettingsClick,
        onDismiss = onDismiss,
    )

    DisplaySettingsSheet(
        show = overlayView == OverlayView.DISPLAY_SETTINGS,
        videoZoomAndContentScaleState = videoZoomAndContentScaleState,
        subtitleTracksState = subtitleTracksState,
        subtitleOptionsState = subtitleOptionsState,
        screenOrientation = playerPreferences.playerScreenOrientation,
        isPanGestureEnabled = playerPreferences.enablePanGesture,
        isAutoPipEnabled = playerPreferences.autoPip,
        isPipSupported = isPipSupported,
        controllerAutoHideTimeout = playerPreferences.controllerAutoHideTimeout,
        onVideoContentScaleChanged = onVideoContentScaleChanged,
        onScreenOrientationChanged = onScreenOrientationChanged,
        onPanGestureChanged = onPanGestureChanged,
        onAutoPipChanged = onAutoPipChanged,
        onControllerAutoHideTimeoutChanged = onControllerAutoHideTimeoutChanged,
        onGesturesClick = onGesturesClick,
        onDismiss = onDismiss,
    )

    VideoInformationSheet(
        show = overlayView == OverlayView.VIDEO_INFORMATION,
        isLoading = isLoadingDetails,
        mediaInfo = mediaInfo,
        player = player,
        mediaPresentationState = mediaPresentationState,
        diagnostics = diagnostics,
        decoderMode = playerPreferences.decoderMode,
        onDismiss = onDismiss,
    )

    BookmarksSheet(
        show = overlayView == OverlayView.BOOKMARKS,
        bookmarks = bookmarks,
        currentPositionMs = mediaPresentationState.position,
        onAddBookmark = onAddBookmark,
        onJumpTo = { positionMs ->
            player.seekTo(positionMs)
            onDismiss()
        },
        onDelete = onDeleteBookmark,
        onDismiss = onDismiss,
    )

    ChaptersSheet(
        show = overlayView == OverlayView.CHAPTERS,
        chapters = chapters,
        currentPositionMs = mediaPresentationState.position,
        onJumpTo = { positionMs ->
            player.seekTo(positionMs)
            onDismiss()
        },
        onDismiss = onDismiss,
    )

    CutSegmentSheet(
        show = overlayView == OverlayView.CUT_SEGMENT,
        cutSegmentState = cutSegmentState,
        onDismiss = onDismiss,
    )

    TutorialSheet(
        show = overlayView == OverlayView.TUTORIAL,
        playerPreferences = playerPreferences,
        onDontShowAgain = onTutorialDontShowAgain,
        onDismiss = onDismiss,
    )
}

val Configuration.isPortrait: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT
