package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import dev.anilbeesetti.nextplayer.feature.player.dialogs.playbackSpeedControlsDialog
import dev.anilbeesetti.nextplayer.feature.player.dialogs.trackSelectionDialog
import dev.anilbeesetti.nextplayer.feature.player.dialogs.videoZoomOptionsDialog
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
    var showControls by remember { mutableStateOf(true) }
    val presentationState = rememberPresentationState(player)
    val metadataState = rememberMetadataState(player)

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
            .background(androidx.compose.ui.graphics.Color.Black)
            .noRippleClickable { showControls = !showControls },
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(
                contentScale = ContentScale.Fit,
                sourceSizeDp = presentationState.videoSizeDp,
            ),
        )

        if (showControls) {
            Column(
                modifier = Modifier.safeDrawingPadding(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FilledTonalIconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                    Text(
                        text = metadataState?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                playbackSpeedControlsDialog(
                                    mediaController = player ?: return@FilledTonalIconButton,
                                    lifecycleScope = lifecycleScope,
                                ).show()
                            },
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_speed),
                                contentDescription = null,
                            )
                        }
                        FilledTonalIconButton(
                            onClick = {
                                trackSelectionDialog(
                                    type = C.TRACK_TYPE_AUDIO,
                                    tracks = player?.currentTracks ?: return@FilledTonalIconButton,
                                    onTrackSelected = { player?.switchAudioTrack(it) },
                                ).show()
                            },
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_audio_track),
                                contentDescription = null,
                            )
                        }
                        FilledTonalIconButton(
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


                // BOTTOM

                Spacer(modifier = Modifier.weight(1f))
                Slider(
                    value = 40f,
                    valueRange = 0f..100f,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    FilledTonalIconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_lock_open),
                            contentDescription = null,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            videoZoomOptionsDialog(
                                currentVideoZoom = playerPreferences.playerVideoZoom,
                                onVideoZoomOptionSelected = { changeAndSaveVideoZoom(videoZoom = it) },
                            ).show()
                        }
                    ) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_fit_screen),
                            contentDescription = null,
                        )
                    }
                    FilledTonalIconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_pip),
                            contentDescription = null,
                        )
                    }
                    FilledTonalIconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_headset),
                            contentDescription = null,
                        )
                    }
                    FilledTonalIconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_loop_off),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}