package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util.getStringForTime
import androidx.media3.ui.compose.state.ProgressStateWithTickInterval
import androidx.media3.ui.compose.state.RepeatButtonState
import androidx.media3.ui.compose.state.ShuffleButtonState
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.common.extensions.round
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.buttons.LoopButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButtonBlackAlpha
import dev.anilbeesetti.nextplayer.feature.player.buttons.ShuffleButton
import dev.anilbeesetti.nextplayer.feature.player.extensions.drawableRes
import dev.anilbeesetti.nextplayer.feature.player.state.ChaptersState
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackParametersState
import dev.anilbeesetti.nextplayer.feature.player.ui.titleOrDefault

private const val MILLISECONDS_PER_SECOND = 1_000L

@OptIn(UnstableApi::class)
@Composable
fun ControlsBottomView(
    modifier: Modifier = Modifier,
    progressState: ProgressStateWithTickInterval,
    chaptersState: ChaptersState,
    repeatButtonState: RepeatButtonState,
    shuffleButtonState: ShuffleButtonState,
    playbackParametersState: PlaybackParametersState,
    controlsAlignment: Alignment.Horizontal,
    videoContentScale: VideoContentScale,
    isPipSupported: Boolean,
    showRemainingTime: Boolean,
    onToggleTimeDisplay: () -> Unit,
    onChaptersClick: () -> Unit,
    onVideoContentScaleClick: () -> Unit,
    onVideoContentScaleLongClick: () -> Unit,
    onLockControlsClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onPlaybackSpeedClick: () -> Unit,
    onRotateClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
) {
    val systemBarsPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val timeButtonFocusRequester = remember { FocusRequester() }
    Column(
        modifier = modifier
            .padding(systemBarsPadding.copy(top = 0.dp))
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .padding(bottom = 16.dp.takeIf { systemBarsPadding.calculateBottomPadding() == 0.dp } ?: 0.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .focusProperties { onEnter = { timeButtonFocusRequester.requestFocus() } }
                .focusGroup(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerButton(
                modifier = Modifier.focusRequester(timeButtonFocusRequester),
                onClick = onToggleTimeDisplay,
                containerColor = PlayerButtonBlackAlpha,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
            ) {
                val timeTextPositionMs by remember(progressState) {
                    derivedStateOf {
                        val wholeSeconds = progressState.currentPositionMs / MILLISECONDS_PER_SECOND
                        wholeSeconds * MILLISECONDS_PER_SECOND
                    }
                }
                Text(
                    text = buildString {
                        val positionText = when (showRemainingTime) {
                            true -> if (progressState.durationMs != C.TIME_UNSET) {
                                val remainingMs = timeTextPositionMs - progressState.durationMs
                                getStringForTime(remainingMs)
                            } else {
                                getStringForTime(C.TIME_UNSET)
                            }

                            false -> getStringForTime(timeTextPositionMs)
                        }
                        append(positionText)
                        append(" / ")
                        append(getStringForTime(progressState.durationMs))
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (chaptersState.chapters.isNotEmpty()) {
                PlayerButton(
                    onClick = onChaptersClick,
                    containerColor = PlayerButtonBlackAlpha,
                    contentPadding = PaddingValues(vertical = 1.dp, horizontal = 8.dp).copy(end = 2.dp),
                ) {
                    Row(
                        modifier = Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = chaptersState.chapters.getOrNull(chaptersState.currentChapterIndex)
                                ?.titleOrDefault(chaptersState.currentChapterIndex)
                                ?: stringResource(R.string.chapters),
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerButton(
                    onClick = onPlaybackSpeedClick,
                    containerColor = PlayerButtonBlackAlpha,
                    contentPadding = PaddingValues(4.dp),
                ) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = playbackParametersState.speed.round(2).toString(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = LocalContentColor.current,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(
                                maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                                minFontSize = 6.sp,
                                stepSize = 0.5.sp,
                            ),
                        )
                    }
                }

                if (!isTv) {
                    PlayerButton(
                        onClick = onRotateClick,
                        containerColor = PlayerButtonBlackAlpha,
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_screen_rotation),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        PlayerSeekbar(
            position = progressState.currentPositionMs.toFloat(),
            duration = progressState.durationMs.toFloat(),
            chapters = chaptersState.chapters,
            onSeek = { onSeek(it.toLong()) },
            onSeekFinished = { onSeekEnd() },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = controlsAlignment),
        ) {
            PlayerButton(onClick = onLockControlsClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock_open),
                    contentDescription = null,
                )
            }
            PlayerButton(
                onClick = onVideoContentScaleClick,
                onLongClick = onVideoContentScaleLongClick,
            ) {
                Icon(
                    painter = painterResource(videoContentScale.drawableRes()),
                    contentDescription = null,
                )
            }
            if (isPipSupported) {
                PlayerButton(onClick = onPictureInPictureClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pip),
                        contentDescription = null,
                    )
                }
            }
            PlayerButton(onClick = onPlayInBackgroundClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_headset),
                    contentDescription = null,
                )
            }
            LoopButton(state = repeatButtonState)
            ShuffleButton(state = shuffleButtonState)
        }
    }
}
