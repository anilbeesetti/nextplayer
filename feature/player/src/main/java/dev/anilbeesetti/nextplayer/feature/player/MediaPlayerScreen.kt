package dev.anilbeesetti.nextplayer.feature.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import dev.anilbeesetti.nextplayer.core.model.VideoZoom
import dev.anilbeesetti.nextplayer.feature.player.dialogs.playbackSpeedControlsDialog
import dev.anilbeesetti.nextplayer.feature.player.dialogs.trackSelectionDialog
import dev.anilbeesetti.nextplayer.feature.player.dialogs.videoZoomOptionsDialog
import dev.anilbeesetti.nextplayer.feature.player.extensions.next
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable
import dev.anilbeesetti.nextplayer.feature.player.extensions.toggleSystemBars
import dev.anilbeesetti.nextplayer.feature.player.service.switchAudioTrack
import dev.anilbeesetti.nextplayer.feature.player.service.switchSubtitleTrack
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMetadataState
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
fun PlayerActivity.MediaPlayerScreen(
    player: MediaController,
    modifier: Modifier = Modifier,
    onSelectSubtitleClick: () -> Unit = {},
) {
    val presentationState = rememberPresentationState(player)
    val metadataState = rememberMetadataState(player)

    var showControls by remember { mutableStateOf(true) }
    var videoZoom by remember { mutableStateOf(playerPreferences.playerVideoZoom) }

    LaunchedEffect(showControls) {
        if (showControls) {
            toggleSystemBars(showBars = true)
//                    delay(5.seconds)
//                    showControls = false
        } else {
            toggleSystemBars(showBars = false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .noRippleClickable { showControls = !showControls },
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(
                contentScale = videoZoom.toContentScale(),
                sourceSizeDp = presentationState.videoSizeDp,
            ),
        )

        if (showControls) {
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
                    // TODO: implement duration
                    Text(
                        text = "00:00",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Text(
                        text = "24:21",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    RotationButton()
                }
                Slider(
                    value = 40f,
                    valueRange = 0f..100f,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
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
                    PlayerButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_pip),
                            contentDescription = null,
                        )
                    }
                    PlayerButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
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

@Composable
fun LoopButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberRepeatButtonState(player)

    PlayerButton(modifier = modifier, onClick = state::onClick) {
        Icon(
            painter = repeatModeIconPainter(state.repeatModeState),
            contentDescription = repeatModeContentDescription(state.repeatModeState),
        )
    }
}

@Composable
private fun repeatModeIconPainter(repeatMode: @Player.RepeatMode Int): Painter {
    return when (repeatMode) {
        Player.REPEAT_MODE_OFF -> painterResource(coreUiR.drawable.ic_loop_off)
        Player.REPEAT_MODE_ONE -> painterResource(coreUiR.drawable.ic_loop_one)
        else -> painterResource(coreUiR.drawable.ic_loop_all)
    }
}

@Composable
private fun repeatModeContentDescription(repeatMode: @Player.RepeatMode Int): String {
    return when (repeatMode) {
        Player.REPEAT_MODE_OFF -> stringResource(coreUiR.string.loop_mode_off)
        Player.REPEAT_MODE_ONE -> stringResource(coreUiR.string.loop_mode_one)
        else -> stringResource(coreUiR.string.loop_mode_all)
    }
}

@Composable
internal fun PlayPauseButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberPlayPauseButtonState(player)
    val icon = when (state.showPlay) {
        true -> painterResource(coreUiR.drawable.ic_play)
        false -> painterResource(coreUiR.drawable.ic_pause)
    }
    val contentDescription = when (state.showPlay) {
        true -> stringResource(coreUiR.string.play_pause)
        false -> stringResource(coreUiR.string.play_pause)
    }

    PlayerButton(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        onClick = state::onClick,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
internal fun PreviousButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberPreviousButtonState(player)

    PlayerButton(modifier = modifier, onClick = state::onClick) {
        Icon(
            painter = painterResource(coreUiR.drawable.ic_skip_prev),
            contentDescription = stringResource(coreUiR.string.player_controls_previous),
        )
    }
}

@Composable
internal fun NextButton(player: Player, modifier: Modifier = Modifier) {
    val state = rememberNextButtonState(player)

    PlayerButton(modifier = modifier, onClick = state::onClick) {
        Icon(
            painter = painterResource(coreUiR.drawable.ic_skip_next),
            contentDescription = stringResource(coreUiR.string.player_controls_next),
        )
    }
}

@Composable
fun RotationButton(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current

    PlayerButton(
        onClick = {
            activity?.requestedOrientation = when (activity.resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        },
    ) {
        Icon(
            painter = painterResource(coreUiR.drawable.ic_screen_rotation),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
fun PlayerButton(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = modifier.padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

private fun VideoZoom.toContentScale(): ContentScale = when (this) {
    VideoZoom.BEST_FIT -> ContentScale.Fit
    VideoZoom.STRETCH -> ContentScale.FillBounds
    VideoZoom.CROP -> ContentScale.Crop
    VideoZoom.HUNDRED_PERCENT -> ContentScale.None // TODO: fix this
}