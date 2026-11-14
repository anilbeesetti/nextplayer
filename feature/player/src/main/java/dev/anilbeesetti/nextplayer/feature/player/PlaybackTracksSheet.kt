package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.extensions.castSubtitleIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.getName
import dev.anilbeesetti.nextplayer.feature.player.state.rememberTracksState
import dev.anilbeesetti.nextplayer.feature.player.ui.RadioButtonRow

@Composable
internal fun PlaybackTrackControls(enabled: Boolean, onTracks: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (type in listOf(
            C.TRACK_TYPE_AUDIO,
            C.TRACK_TYPE_TEXT,
        )) {
            val audio = type == C.TRACK_TYPE_AUDIO
            FilledTonalButton(
                onClick = { onTracks(type) },
                enabled = enabled,
                modifier = Modifier.weight(1f).tvFocusRing(),
            ) {
                Icon(
                    painterResource(
                        if (audio) R.drawable.ic_audio_track else R.drawable.ic_subtitle_track,
                    ),
                    contentDescription = null,
                )
                Text(
                    stringResource(if (audio) R.string.audio else R.string.subtitle),
                    Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackTracksSheet(
    player: Player,
    trackType: Int,
    onSelectSubtitle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTracksState(
        player,
        trackType,
    )
    val audio = trackType == C.TRACK_TYPE_AUDIO
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp)
                .selectableGroup().testTag("playback_tracks"),
        ) {
            Text(
                stringResource(
                    if (audio) R.string.select_audio_track else R.string.select_subtitle_track,
                ),
                Modifier.padding(bottom = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (state.tracks.isEmpty()) {
                Text(
                    stringResource(
                        if (audio) {
                            R.string.no_audio_tracks_found
                        } else {
                            R.string.no_subtitle_tracks_found
                        },
                    ),
                )
            }
            state.tracks.forEachIndexed { index, track ->
                RadioButtonRow(
                    selected = track.isSelected,
                    // Media3's Cast adapter drops MediaTrack.name; retain local sidecar labels.
                    text = track.mediaTrackGroup.castSubtitleIndex?.let {
                        player.currentMediaItem?.localConfiguration?.subtitleConfigurations
                            ?.getOrNull(it)?.label
                    } ?: track.mediaTrackGroup.getName(
                        trackType,
                        index,
                    ),
                    onClick = {
                        state.switchTrack(index)
                        onDismiss()
                    },
                )
            }
            if (!audio) {
                RadioButtonRow(
                    selected = state.tracks.none { it.isSelected },
                    text = stringResource(R.string.disable),
                    onClick = {
                        state.switchTrack(-1)
                        onDismiss()
                    },
                )
                FilledTonalButton(
                    onClick = {
                        onSelectSubtitle()
                        onDismiss()
                    },
                    enabled = player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).tvFocusRing(),
                ) { Text(stringResource(R.string.open_subtitle)) }
            }
        }
    }
}
