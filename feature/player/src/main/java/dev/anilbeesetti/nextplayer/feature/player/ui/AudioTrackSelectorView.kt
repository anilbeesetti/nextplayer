package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.extensions.getName
import dev.anilbeesetti.nextplayer.feature.player.service.switchAudioTrack
import dev.anilbeesetti.nextplayer.feature.player.state.rememberTracksState

@OptIn(UnstableApi::class)
@Composable
fun AudioTrackSelectorView(
    modifier: Modifier = Modifier,
    player: MediaController,
    onDismiss: () -> Unit,
) {
    val audioTracksState = rememberTracksState(player, C.TRACK_TYPE_AUDIO)

    OverlayView(
        title = stringResource(R.string.select_audio_track),
    ) {
        Column(modifier = modifier.selectableGroup()) {
            audioTracksState.tracks.forEachIndexed { index, track ->
                RadioButtonRow(
                    selected = track.isSelected,
                    text = track.mediaTrackGroup.getName(C.TRACK_TYPE_AUDIO, index),
                    onClick = {
                        player.switchAudioTrack(index)
                        onDismiss()
                    },
                )
            }
            RadioButtonRow(
                selected = audioTracksState.tracks.none { it.isSelected },
                text = stringResource(R.string.disable),
                onClick = {
                    player.switchAudioTrack(-1)
                    onDismiss()
                },
            )
        }
    }
}