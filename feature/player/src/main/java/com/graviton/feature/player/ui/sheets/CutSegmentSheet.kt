package com.graviton.feature.player.ui.sheets

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.player.extensions.formatted
import com.graviton.feature.player.state.CutSegmentState
import com.graviton.feature.player.ui.OverlayView
import kotlin.time.Duration.Companion.milliseconds

/**
 * A-B segment control: mark a start and an end, then playback loops inside that range.
 *
 * This is a playback tool, not an editor - nothing is written back to the file.
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.CutSegmentSheet(
    modifier: Modifier = Modifier,
    show: Boolean,
    cutSegmentState: CutSegmentState,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.cut_segment),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.cut_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )

            val notSet = stringResource(R.string.cut_not_set)
            SheetInfoRow(
                label = stringResource(R.string.cut_start),
                value = cutSegmentState.startMs?.milliseconds?.formatted() ?: notSet,
                monospace = true,
            )
            SheetInfoRow(
                label = stringResource(R.string.cut_end),
                value = cutSegmentState.endMs?.milliseconds?.formatted() ?: notSet,
                monospace = true,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = cutSegmentState::setStartToCurrentPosition,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.cut_set_start))
                }
                FilledTonalButton(
                    onClick = cutSegmentState::setEndToCurrentPosition,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.cut_set_end))
                }
            }

            SheetSwitchRow(
                icon = NextIcons.Repeat,
                title = stringResource(R.string.cut_loop),
                checked = cutSegmentState.loopEnabled,
                enabled = cutSegmentState.hasSegment,
                onCheckedChange = cutSegmentState::setLoopEnabled,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = cutSegmentState::jumpToStart,
                    enabled = cutSegmentState.startMs != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.cut_start))
                }
                OutlinedButton(
                    onClick = cutSegmentState::clear,
                    enabled = cutSegmentState.startMs != null || cutSegmentState.endMs != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.cut_clear))
                }
            }
        }
    }
}
