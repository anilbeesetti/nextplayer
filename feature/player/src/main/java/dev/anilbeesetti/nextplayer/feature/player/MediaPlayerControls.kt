package dev.anilbeesetti.nextplayer.feature.player

import android.content.ContentResolver
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import dev.anilbeesetti.nextplayer.core.common.extensions.getMediaContentUri
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.requestFocusUntilLanded
import dev.anilbeesetti.nextplayer.core.ui.components.thenIf
import dev.anilbeesetti.nextplayer.feature.player.state.ControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.SeekGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.TapGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomAndContentScaleState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberChaptersState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaPresentationState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMetadataState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlayerKeyInputState
import dev.anilbeesetti.nextplayer.feature.player.ui.DpadSeekIndicator
import dev.anilbeesetti.nextplayer.feature.player.ui.OverlayShowView
import dev.anilbeesetti.nextplayer.feature.player.ui.OverlayView
import dev.anilbeesetti.nextplayer.feature.player.ui.PlayerGestureIndicators
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.inlineTopActionCount
import dev.anilbeesetti.nextplayer.feature.player.utils.createVideoShareIntent
import dev.anilbeesetti.nextplayer.feature.player.utils.isMediaStoreVideo
import dev.anilbeesetti.nextplayer.feature.player.utils.mediaInfoSnapshot
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Controls and overlays shared by live playback and the previewable content. */
@OptIn(UnstableApi::class)
@Composable
internal fun MediaPlayerControls(
    player: Player,
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    isPipSupported: Boolean,
    onPictureInPictureClick: () -> Unit,
    modifier: Modifier = Modifier,
    dpadUpDown: Int? = null,
) {
    val playerPreferences = state.playerPreferences
    val metadataState = rememberMetadataState(player = player)
    val mediaPresentationState = rememberMediaPresentationState(player = player)
    val progressState = rememberProgressStateWithTickInterval(player = player)
    val chaptersState = rememberChaptersState(
        player = player,
        progressState = progressState,
    )
    val keyInputState = rememberPlayerKeyInputState(
        player = player,
        controls = controlsVisibilityState,
        seekIncrementMs = playerPreferences.seekIncrement.seconds.inWholeMilliseconds,
        dpadUpDown = dpadUpDown,
    )
    var overlayView by remember { mutableStateOf<OverlayView?>(value = null) }
    val mediaInfo = state.mediaInfo.takeIf { overlayView == OverlayView.MORE_OPTIONS }
    LaunchedEffect(metadataState.mediaId) {
        if (overlayView == OverlayView.CHAPTERS) overlayView = null
    }

    fun showOverlay(overlay: OverlayView) {
        if (state.mediaInfo != null) onAction(PlayerAction.DismissMediaInfo)
        controlsVisibilityState.hideControls()
        overlayView = overlay
    }

    fun dismissOverlay() {
        if (state.mediaInfo != null) onAction(PlayerAction.DismissMediaInfo)
        overlayView = null
    }

    val context = LocalContext.current
    val videoUri = player.currentMediaItem?.localConfiguration?.uri
    val scope = rememberCoroutineScope()
    fun currentShareUri() = player.currentMediaItem?.localConfiguration?.uri?.takeIf {
        it.scheme == ContentResolver.SCHEME_CONTENT || it.scheme == ContentResolver.SCHEME_FILE
    }

    fun shareCurrentVideo() {
        val uri = currentShareUri() ?: return
        scope.launch {
            val contentUri = if (uri.scheme == ContentResolver.SCHEME_FILE) {
                // shortcut: file URIs need a MediaStore entry until unindexed videos have an export path.
                withContext(Dispatchers.IO) { context.getMediaContentUri(uri) }
            } else {
                uri
            }
            val shared = contentUri?.let {
                runCatching { context.startActivity(createVideoShareIntent(it)) }.isSuccess
            } ?: false
            if (shared) {
                dismissOverlay()
            } else {
                Toast.makeText(
                    context,
                    R.string.error_sharing_video,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val isTv = remember { context.isTelevision }
    val handleDpadKeys = isTv || dpadUpDown != null
    val rootFocusRequester = remember { FocusRequester() }
    val middleControlsFocusRequester = remember { FocusRequester() }
    val unlockFocusRequester = remember { FocusRequester() }
    var isMiddleControlsFocused by remember { mutableStateOf(value = false) }
    var isUnlockFocused by remember { mutableStateOf(value = false) }
    if (handleDpadKeys) {
        LaunchedEffect(
            controlsVisibilityState.controlsVisible,
            controlsVisibilityState.controlsLocked,
            key3 = overlayView,
        ) {
            if (overlayView != null) return@LaunchedEffect
            if (!isTv || !controlsVisibilityState.controlsVisible) {
                runCatching { rootFocusRequester.requestFocus() }
                return@LaunchedEffect
            }
            val locked = controlsVisibilityState.controlsLocked
            val target = if (locked) unlockFocusRequester else middleControlsFocusRequester
            target.requestFocusUntilLanded(attempts = 20) {
                if (locked) isUnlockFocused else isMiddleControlsFocused
            }
        }
    }

    CompositionLocalProvider(
        LocalControlsVisibilityState provides controlsVisibilityState,
        LocalUseMaterialYouControls provides playerPreferences.useMaterialYouControls,
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .thenIf(condition = handleDpadKeys) {
                    focusRequester(focusRequester = rootFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { keyEvent ->
                            if (overlayView != null ||
                                (
                                    !isTv && keyEvent.key != Key.DirectionUp &&
                                        keyEvent.key != Key.DirectionDown
                                    )
                            ) {
                                false
                            } else {
                                keyInputState.onKeyEvent(keyEvent = keyEvent)
                            }
                        }
                },
        ) {
            val inlineActionCount = inlineTopActionCount(maxWidth)
            if (mediaPresentationState.isBuffering) {
                CircularProgressIndicator(
                    Modifier
                        .align(Alignment.Center)
                        .size(72.dp),
                )
            }
            PlayerGestureIndicators(tapGestureState = tapGestureState)
            DpadSeekIndicator(
                visible = keyInputState.isSeeking && keyInputState.seekOffsetMs != 0L,
                offsetMs = keyInputState.seekOffsetMs,
                positionMs = keyInputState.seekPositionMs,
            )

            if (controlsVisibilityState.controlsVisible && controlsVisibilityState.controlsLocked) {
                PlayerLockedControls(
                    onUnlockControls = controlsVisibilityState::unlockControls,
                    unlockButtonModifier = Modifier.thenIf(condition = isTv) {
                        focusRequester(focusRequester = unlockFocusRequester)
                            .onFocusChanged { isUnlockFocused = it.hasFocus }
                    },
                )
            } else {
                PlayerControls(
                    player = player,
                    title = state.renamedVideoTitles[videoUri] ?: metadataState.title.orEmpty(),
                    playerPreferences = playerPreferences,
                    videoDecoderMode = state.decoderServiceState.videoMode,
                    inlineActionCount = inlineActionCount,
                    controlsVisibilityState = controlsVisibilityState,
                    seekGestureState = seekGestureState,
                    videoZoomAndContentScaleState = videoZoomAndContentScaleState,
                    progressState = progressState,
                    chaptersState = chaptersState,
                    isPipSupported = isPipSupported,
                    onShowOverlay = ::showOverlay,
                    onBackClick = { onAction(PlayerAction.NavigateUp) },
                    onPlayInBackgroundClick = { onAction(PlayerAction.PlayInBackground) },
                    onToggleTimeDisplay = { onAction(PlayerAction.ToggleTimeDisplay) },
                    onPictureInPictureClick = onPictureInPictureClick,
                    middleControlsModifier = Modifier.thenIf(condition = isTv) {
                        focusRequester(focusRequester = middleControlsFocusRequester)
                            .onFocusChanged { isMiddleControlsFocused = it.hasFocus }
                    },
                )
            }

            OverlayShowView(
                player = player,
                overlayView = overlayView,
                chapters = chaptersState.chapters,
                currentChapterIndex = chaptersState.currentChapterIndex,
                onChapterSelected = { chapter ->
                    if (player.isCurrentMediaItemSeekable) {
                        player.seekTo(chapter.startTimeMs)
                        overlayView = null
                        controlsVisibilityState.showControls()
                    }
                },
                videoDecoderMode = state.decoderServiceState.videoMode,
                audioDecoderMode = state.decoderServiceState.audioMode,
                videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                renamedVideoTitles = state.renamedVideoTitles,
                inlineActionCount = inlineActionCount,
                mediaInfo = mediaInfo,
                onDismiss = ::dismissOverlay,
                onShowOverlay = ::showOverlay,
                onShare = (::shareCurrentVideo).takeIf { currentShareUri() != null },
                onRename = if (videoUri?.isMediaStoreVideo() == true) {
                    {
                        dismissOverlay()
                        onAction(PlayerAction.RequestRename)
                    }
                } else {
                    null
                },
                onInfo = if (player.currentMediaItem != null) {
                    {
                        val currentUri = player.currentMediaItem?.localConfiguration?.uri
                        player.mediaInfoSnapshot(
                            fallbackName = context.getString(R.string.unknown),
                            renamedName = state.renamedVideoTitles[currentUri],
                        )?.let { info ->
                            onAction(PlayerAction.ShowMediaInfo(info))
                        }
                    }
                } else {
                    null
                },
                onDismissInfo = { onAction(PlayerAction.DismissMediaInfo) },
                onVideoDecoderModeSelected = {
                    onAction(PlayerAction.SetVideoDecoderMode(mode = it))
                },
                onAudioDecoderModeSelected = {
                    onAction(PlayerAction.SetAudioDecoderMode(mode = it))
                },
                onSelectSubtitleClick = { onAction(PlayerAction.SelectSubtitle) },
                onSelectAudioClick = { onAction(PlayerAction.SelectAudio) },
                onSubtitleOptionEvent = {
                    onAction(PlayerAction.OnSubtitleOptionEvent(event = it))
                },
                onVideoContentScaleChanged =
                    videoZoomAndContentScaleState::onVideoContentScaleChanged,
            )
        }
    }

    BackHandler {
        when {
            mediaInfo != null -> onAction(PlayerAction.DismissMediaInfo)

            overlayView != null -> dismissOverlay()

            isTv && controlsVisibilityState.controlsVisible ->
                controlsVisibilityState.hideControls()

            else -> onAction(PlayerAction.NavigateUp)
        }
    }
}
