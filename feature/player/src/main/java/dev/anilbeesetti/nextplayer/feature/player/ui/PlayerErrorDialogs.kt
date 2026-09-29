package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.common.PlaybackException
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryState
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderRecoveryStatus
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
import dev.anilbeesetti.nextplayer.feature.player.model.labelRes

@Composable
internal fun PlayerErrorDialogs(
    decoderRecoveryState: DecoderRecoveryState,
    playbackError: PlaybackException?,
    hasNextMediaItem: Boolean,
    onTryDecoderFallback: () -> Unit,
    onPlayNextVideo: () -> Unit,
    onExit: () -> Unit,
) {
    val unsupportedMode = decoderRecoveryState.unsupportedMode
    val recoveryTrackType = decoderRecoveryState.trackType
    if (
        decoderRecoveryState.status == DecoderRecoveryStatus.AWAITING_CONFIRMATION &&
        unsupportedMode != null &&
        recoveryTrackType != null
    ) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(text = stringResource(coreUiR.string.decoder))
            },
            text = {
                Text(
                    text = stringResource(
                        when (recoveryTrackType) {
                            DecoderTrackType.VIDEO -> coreUiR.string.video_decoder_mode_not_supported
                            DecoderTrackType.AUDIO -> coreUiR.string.audio_decoder_mode_not_supported
                        },
                        stringResource(unsupportedMode.labelRes),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onTryDecoderFallback) {
                    Text(text = stringResource(coreUiR.string.okay))
                }
            },
        )
    }

    val allDecoderModesFailed = decoderRecoveryState.status == DecoderRecoveryStatus.FAILED
    val showPlayerError = allDecoderModesFailed ||
        (
            decoderRecoveryState.status == DecoderRecoveryStatus.NONE &&
                playbackError != null
            )
    if (showPlayerError) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(text = stringResource(coreUiR.string.error_playing_video))
            },
            text = {
                Text(
                    text = playbackError?.message
                        ?: stringResource(
                            if (allDecoderModesFailed) {
                                coreUiR.string.no_supported_decoder
                            } else {
                                coreUiR.string.unknown_error
                            },
                        ),
                )
            },
            confirmButton = {
                if (hasNextMediaItem) {
                    TextButton(onClick = onPlayNextVideo) {
                        Text(text = stringResource(coreUiR.string.play_next_video))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onExit) {
                    Text(text = stringResource(coreUiR.string.exit))
                }
            },
        )
    }
}
