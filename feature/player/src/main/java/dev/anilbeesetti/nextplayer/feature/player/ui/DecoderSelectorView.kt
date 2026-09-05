package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.model.DecoderTrackType
import dev.anilbeesetti.nextplayer.feature.player.model.descriptionRes
import dev.anilbeesetti.nextplayer.feature.player.model.labelRes
import dev.anilbeesetti.nextplayer.feature.player.model.selectableDecoderModes
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode

@Composable
fun BoxScope.DecoderSelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    videoMode: DecoderMode?,
    audioMode: DecoderMode?,
    onVideoModeSelected: (DecoderMode) -> Unit,
    onAudioModeSelected: (DecoderMode) -> Unit,
) {
    var selectedTrackType by rememberSaveable { mutableStateOf(DecoderTrackType.VIDEO) }

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.select_decoders),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            DecoderTrackTypeSelector(
                selectedTrackType = selectedTrackType,
                onTrackTypeSelected = { selectedTrackType = it },
            )
            DecoderModeGroup(
                selectedMode = when (selectedTrackType) {
                    DecoderTrackType.VIDEO -> videoMode
                    DecoderTrackType.AUDIO -> audioMode
                },
                onModeSelected = when (selectedTrackType) {
                    DecoderTrackType.VIDEO -> onVideoModeSelected
                    DecoderTrackType.AUDIO -> onAudioModeSelected
                },
            )
        }
    }
}

@Composable
private fun DecoderTrackTypeSelector(
    selectedTrackType: DecoderTrackType,
    onTrackTypeSelected: (DecoderTrackType) -> Unit,
) {
    val trackTypes = DecoderTrackType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        trackTypes.forEachIndexed { index, trackType ->
            SegmentedButton(
                selected = selectedTrackType == trackType,
                onClick = { onTrackTypeSelected(trackType) },
                shape = SegmentedButtonDefaults.itemShape(index, trackTypes.size),
            ) {
                Text(
                    text = stringResource(
                        when (trackType) {
                            DecoderTrackType.VIDEO -> R.string.video
                            DecoderTrackType.AUDIO -> R.string.audio
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun DecoderModeGroup(
    selectedMode: DecoderMode?,
    onModeSelected: (DecoderMode) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        selectableDecoderModes.forEach { mode ->
            DecoderModeRow(
                mode = mode,
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
            )
        }
    }
}

@Composable
private fun DecoderModeRow(
    mode: DecoderMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column {
            Text(
                text = stringResource(mode.labelRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(mode.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
