package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.VideoZoom
import dev.anilbeesetti.nextplayer.feature.player.buttons.LoopButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.NextButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayPauseButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PreviousButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.RotationButton
import dev.anilbeesetti.nextplayer.feature.player.dialogs.playbackSpeedControlsDialog
import dev.anilbeesetti.nextplayer.feature.player.dialogs.trackSelectionDialog
import dev.anilbeesetti.nextplayer.feature.player.dialogs.videoZoomOptionsDialog
import dev.anilbeesetti.nextplayer.feature.player.extensions.next
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekBack
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekForward
import dev.anilbeesetti.nextplayer.feature.player.extensions.setScrubbingModeEnabled
import dev.anilbeesetti.nextplayer.feature.player.extensions.shouldFastSeek
import dev.anilbeesetti.nextplayer.feature.player.service.switchAudioTrack
import dev.anilbeesetti.nextplayer.feature.player.service.switchSubtitleTrack
import dev.anilbeesetti.nextplayer.feature.player.state.durationFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.pendingPositionFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.positionFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.rememberControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaPresentationState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMetadataState
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerGestureHelper.Companion.SEEK_STEP_MS
import dev.anilbeesetti.nextplayer.feature.player.utils.toMillis
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
fun PlayerActivity.MediaPlayerScreen(
    player: MediaController,
    modifier: Modifier = Modifier,
    onSelectSubtitleClick: () -> Unit = {},
) {
    val presentationState = rememberPresentationState(player)
    val mediaPresentationState = rememberMediaPresentationState(player)
    val metadataState = rememberMetadataState(player)
    val controlsVisibilityState = rememberControlsVisibilityState(
        player = player,
        hideAfter = playerPreferences.controllerAutoHideTimeout.toMillis.milliseconds,
    )

    var videoZoom by remember { mutableStateOf(playerPreferences.playerVideoZoom) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisibilityState.toggleControlsVisibility() },
                    onDoubleTap = { offset ->
                        if (controlsVisibilityState.controlsLocked) return@detectTapGestures

                        val action = when (playerPreferences.doubleTapGesture) {
                            DoubleTapGesture.FAST_FORWARD_AND_REWIND -> {
                                val viewCenterX = size.width / 2
                                when {
                                    offset.x < viewCenterX -> DoubleTapAction.SEEK_BACKWARD
                                    else -> DoubleTapAction.SEEK_FORWARD
                                }
                            }

                            DoubleTapGesture.BOTH -> {
                                val eventPositionX = offset.x / size.width
                                when {
                                    eventPositionX < 0.35 -> DoubleTapAction.SEEK_BACKWARD
                                    eventPositionX > 0.65 -> DoubleTapAction.SEEK_FORWARD
                                    else -> DoubleTapAction.PLAY_PAUSE
                                }
                            }

                            DoubleTapGesture.PLAY_PAUSE -> DoubleTapAction.PLAY_PAUSE

                            DoubleTapGesture.NONE -> return@detectTapGestures
                        }

                        when (action) {
                            DoubleTapAction.SEEK_BACKWARD -> {
                                player.seekBack(
                                    positionMs = player.currentPosition - playerPreferences.seekIncrement.toMillis,
                                    shouldFastSeek = playerPreferences.shouldFastSeek(player.duration)
                                )
                            }
                            DoubleTapAction.SEEK_FORWARD -> {
                                player.seekForward(
                                    positionMs = player.currentPosition + playerPreferences.seekIncrement.toMillis,
                                    shouldFastSeek = playerPreferences.shouldFastSeek(player.duration)
                                )
                            }
                            DoubleTapAction.PLAY_PAUSE -> {
                                when (player.isPlaying) {
                                    true -> player.pause()
                                    false -> player.play()
                                }
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                var seekStart = player.currentPosition
                var seekChange = 0L
                var position = 0L

                detectHorizontalDragGestures(
                    onDragStart = {
                        if (controlsVisibilityState.controlsLocked) return@detectHorizontalDragGestures

                        player.setScrubbingModeEnabled(true)
                        seekStart = player.currentPosition
                        seekChange = 0L
                        position = 0L
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (controlsVisibilityState.controlsLocked) return@detectHorizontalDragGestures

                        val changeDiff = change.previousPosition.x - change.position.x
                        val distanceDiff = abs(changeDiff / 4).coerceIn(0.5f, 10f)
                        val changeValue = (distanceDiff * SEEK_STEP_MS).toLong()

                        if (changeDiff < 0) {
                            seekChange = (seekChange + changeValue)
                                .takeIf { it + seekStart < player.duration } ?: (player.duration - seekStart)
                            position = (seekStart + seekChange).coerceAtMost(player.duration)
                            player.seekForward(
                                positionMs = position,
                                shouldFastSeek = playerPreferences.shouldFastSeek(player.duration)
                            )
                        } else {
                            seekChange = (seekChange - changeValue)
                                .takeIf { it + seekStart > 0 } ?: (0 - seekStart)
                            position = seekStart + seekChange
                            player.seekBack(
                                positionMs = position,
                                shouldFastSeek = playerPreferences.shouldFastSeek(player.duration)
                            )
                        }

                        change.consume()
                    },
                    onDragEnd = {
                        player.setScrubbingModeEnabled(false)
                    }
                )
            },
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(
                contentScale = videoZoom.toContentScale(),
                sourceSizeDp = presentationState.videoSizeDp,
            ),
        )

        if (controlsVisibilityState.controlsVisible) {
            if (controlsVisibilityState.controlsLocked) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 8.dp),
                ) {
                    PlayerButton(onClick = { controlsVisibilityState.unlockControls() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_lock),
                            contentDescription = stringResource(coreUiR.string.controls_unlock),
                        )
                    }
                }

                return@Box
            }

            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(horizontal = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PlayerButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                    Text(
                        text = metadataState.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlayerButton(
                            onClick = {
                                playbackSpeedControlsDialog(
                                    mediaController = player,
                                    lifecycleScope = lifecycleScope,
                                ).show()
                            },
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_speed),
                                contentDescription = null,
                            )
                        }
                        PlayerButton(
                            onClick = {
                                trackSelectionDialog(
                                    type = C.TRACK_TYPE_AUDIO,
                                    tracks = player.currentTracks,
                                    onTrackSelected = { player.switchAudioTrack(it) },
                                ).show()
                            },
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_audio_track),
                                contentDescription = null,
                            )
                        }
                        PlayerButton(
                            onClick = {
                                trackSelectionDialog(
                                    type = C.TRACK_TYPE_TEXT,
                                    tracks = player.currentTracks,
                                    onTrackSelected = { player.switchSubtitleTrack(it) },
                                    onOpenLocalTrackClicked = onSelectSubtitleClick,
                                ).show()
                            },
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_subtitle_track),
                                contentDescription = null,
                            )
                        }
                    }
                }

                // MIDDLE
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp, alignment = Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviousButton(player = player)
                    PlayPauseButton(player = player)
                    NextButton(player = player)
                }


                // BOTTOM
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var showPendingPosition by remember { mutableStateOf(false) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.noRippleClickable { showPendingPosition = !showPendingPosition },
                    ) {
                        Text(
                            text = when (showPendingPosition) {
                                true -> "-${mediaPresentationState.pendingPositionFormatted}"
                                false -> mediaPresentationState.positionFormatted
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        Text(
                            text = " / ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        Text(
                            text = mediaPresentationState.durationFormatted,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    RotationButton()
                }
                Slider(
                    value = mediaPresentationState.position.toFloat(),
                    valueRange = 0f..mediaPresentationState.duration.toFloat(),
                    onValueChange = {
                        player.setScrubbingModeEnabled(true)
                        player.seekTo(it.toLong())
                    },
                    onValueChangeFinished = {
                        player.setScrubbingModeEnabled(false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerButton(onClick = { controlsVisibilityState.lockControls() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_lock_open),
                            contentDescription = null,
                        )
                    }
                    PlayerButton(
                        onClick = {
                            val nextVideoZoom = videoZoom.next()
                            videoZoom = nextVideoZoom
                            changeAndSaveVideoZoom(videoZoom = nextVideoZoom)
                        },
                        onLongClick = {
                            videoZoomOptionsDialog(
                                currentVideoZoom = videoZoom,
                                onVideoZoomOptionSelected = {
                                    videoZoom = it
                                    changeAndSaveVideoZoom(videoZoom = it)
                                },
                            ).show()
                        },
                    ) {
                        Icon(
                            painter = when (videoZoom) {
                                VideoZoom.BEST_FIT -> painterResource(coreUiR.drawable.ic_fit_screen)
                                VideoZoom.STRETCH -> painterResource(coreUiR.drawable.ic_aspect_ratio)
                                VideoZoom.CROP -> painterResource(coreUiR.drawable.ic_crop_landscape)
                                VideoZoom.HUNDRED_PERCENT -> painterResource(coreUiR.drawable.ic_width_wide)
                            },
                            contentDescription = null,
                        )
                    }
                    PlayerButton(onClick = { }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_pip),
                            contentDescription = null,
                        )
                    }
                    PlayerButton(onClick = { }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_headset),
                            contentDescription = null,
                        )
                    }
                    LoopButton(player = player)
                }
            }
        }
    }
}

private enum class DoubleTapAction {
    SEEK_BACKWARD,
    SEEK_FORWARD,
    PLAY_PAUSE,
}


private fun VideoZoom.toContentScale(): ContentScale = when (this) {
    VideoZoom.BEST_FIT -> ContentScale.Fit
    VideoZoom.STRETCH -> ContentScale.FillBounds
    VideoZoom.CROP -> ContentScale.Crop
    VideoZoom.HUNDRED_PERCENT -> ContentScale.None // TODO: fix this
}