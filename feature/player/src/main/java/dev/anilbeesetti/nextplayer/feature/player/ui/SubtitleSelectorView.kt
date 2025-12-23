package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.extensions.getName
import dev.anilbeesetti.nextplayer.feature.player.state.rememberTracksState

@Composable
fun BoxScope.SubtitleSelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    player: Player,
    onSelectSubtitleClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val subtitleTracksState = rememberTracksState(player, C.TRACK_TYPE_TEXT)

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.select_subtitle_track),
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            subtitleTracksState.tracks.forEachIndexed { index, track ->
                RadioButtonRow(
                    selected = track.isSelected,
                    text = track.mediaTrackGroup.getName(C.TRACK_TYPE_TEXT, index),
                    onClick = {
                        subtitleTracksState.switchTrack(index)
                        onDismiss()
                    },
                )
            }
            RadioButtonRow(
                selected = subtitleTracksState.tracks.none { it.isSelected },
                text = stringResource(R.string.disable),
                onClick = {
                    subtitleTracksState.switchTrack(-1)
                    onDismiss()
                },
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSelectSubtitleClick()
                onDismiss()
            },
        ) {
            Text(text = stringResource(R.string.open_subtitle))
        }
    }
}
