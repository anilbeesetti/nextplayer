package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.ui.AudioTrackRows
import dev.anilbeesetti.nextplayer.feature.player.ui.SubtitleTrackRows

/** Audio or subtitle track choice for playback that has no video screen open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackTracksSheet(
    player: Player,
    trackType: @C.TrackType Int,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            stringResource(
                if (trackType == C.TRACK_TYPE_AUDIO) {
                    R.string.select_audio_track
                } else {
                    R.string.select_subtitle_track
                },
            ),
            Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 16.dp).selectableGroup().testTag("playback_tracks"),
        ) {
            if (trackType == C.TRACK_TYPE_AUDIO) {
                AudioTrackRows(
                    player,
                    onSelected = onDismiss,
                )
            } else {
                SubtitleTrackRows(
                    player,
                    onSelected = onDismiss,
                )
            }
        }
    }
}
