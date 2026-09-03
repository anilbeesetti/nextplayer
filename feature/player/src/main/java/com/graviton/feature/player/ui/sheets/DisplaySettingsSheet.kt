package com.graviton.feature.player.ui.sheets

import androidx.annotation.OptIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.graviton.core.model.ScreenOrientation
import com.graviton.core.model.VideoContentScale
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.player.extensions.getName
import com.graviton.feature.player.extensions.nameRes
import com.graviton.feature.player.state.SubtitleOptionsState
import com.graviton.feature.player.state.TracksState
import com.graviton.feature.player.state.VideoZoomAndContentScaleState
import com.graviton.feature.player.ui.OverlayView
import com.graviton.feature.player.ui.RadioButtonRow
import kotlin.math.roundToInt

/**
 * DISPLAY / SUBTITLES / PLAYBACK controls for the current playback session.
 *
 * Every control writes through an existing state holder or an existing player preference, so there
 * is nothing decorative in this sheet.
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.DisplaySettingsSheet(
    modifier: Modifier = Modifier,
    show: Boolean,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    subtitleTracksState: TracksState,
    subtitleOptionsState: SubtitleOptionsState,
    screenOrientation: ScreenOrientation,
    isPanGestureEnabled: Boolean,
    isAutoPipEnabled: Boolean,
    isPipSupported: Boolean,
    controllerAutoHideTimeout: Int,
    onVideoContentScaleChanged: (VideoContentScale) -> Unit,
    onScreenOrientationChanged: (ScreenOrientation) -> Unit,
    onPanGestureChanged: (Boolean) -> Unit,
    onAutoPipChanged: (Boolean) -> Unit,
    onControllerAutoHideTimeoutChanged: (Int) -> Unit,
    onGesturesClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.display_settings),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SheetSectionTitle(text = stringResource(R.string.section_display))

            ChipRow(
                sectionLabel = stringResource(R.string.aspect_ratio),
                selectedLabel = stringResource(videoZoomAndContentScaleState.videoContentScale.nameRes()),
                options = VideoContentScale.entries,
                labelFor = { stringResource(it.nameRes()) },
                isSelected = { it == videoZoomAndContentScaleState.videoContentScale },
                onSelect = onVideoContentScaleChanged,
            )

            val zoomPercent = (videoZoomAndContentScaleState.zoom * 100).roundToInt()
            val zoomLabel = stringResource(R.string.zoom)
            SheetInfoRow(
                label = zoomLabel,
                value = stringResource(R.string.zoom_value, zoomPercent),
                monospace = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = NextIcons.Pinch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = videoZoomAndContentScaleState.zoom,
                    onValueChange = videoZoomAndContentScaleState::setZoom,
                    onValueChangeFinished = videoZoomAndContentScaleState::onZoomPanGestureEnd,
                    valueRange = VideoZoomAndContentScaleState.MIN_ZOOM..VideoZoomAndContentScaleState.MAX_ZOOM,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = zoomLabel },
                )
                TextButton(onClick = videoZoomAndContentScaleState::resetZoomAndPan) {
                    Text(text = stringResource(R.string.reset))
                }
            }

            SheetSwitchRow(
                icon = NextIcons.Pan,
                title = stringResource(R.string.pan),
                supportingText = stringResource(R.string.pan_desc),
                checked = isPanGestureEnabled,
                onCheckedChange = onPanGestureChanged,
            )

            ChipRow(
                sectionLabel = stringResource(R.string.orientation),
                selectedLabel = stringResource(screenOrientation.nameRes()),
                options = ScreenOrientation.entries,
                labelFor = { stringResource(it.nameRes()) },
                isSelected = { it == screenOrientation },
                onSelect = onScreenOrientationChanged,
            )

            SheetSectionTitle(text = stringResource(R.string.section_subtitles))
            if (subtitleTracksState.tracks.isEmpty()) {
                SheetInfoRow(
                    label = stringResource(R.string.subtitle),
                    value = stringResource(R.string.subtitle_track_none),
                )
            } else {
                val subtitlesEnabled = subtitleTracksState.tracks.any { it.isSelected }
                SheetSwitchRow(
                    icon = NextIcons.Caption,
                    title = stringResource(R.string.enable_subtitles),
                    checked = subtitlesEnabled,
                    onCheckedChange = { enabled -> subtitleTracksState.switchTrack(if (enabled) 0 else -1) },
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    subtitleTracksState.tracks.forEachIndexed { index, track ->
                        RadioButtonRow(
                            selected = track.isSelected,
                            text = track.mediaTrackGroup.getName(C.TRACK_TYPE_TEXT, index),
                            onClick = { subtitleTracksState.switchTrack(index) },
                        )
                    }
                }
                StepperRow(
                    label = stringResource(R.string.subtitle_delay),
                    value = stringResource(
                        R.string.subtitle_delay_value,
                        "%.2f".format(subtitleOptionsState.delayMilliseconds / 1000.0),
                    ),
                    onDecrement = { subtitleOptionsState.setDelay(subtitleOptionsState.delayMilliseconds - 50) },
                    onIncrement = { subtitleOptionsState.setDelay(subtitleOptionsState.delayMilliseconds + 50) },
                )
                StepperRow(
                    label = stringResource(R.string.subtitle_speed),
                    value = stringResource(
                        R.string.subtitle_speed_value,
                        "%.2f".format(subtitleOptionsState.speedMultiplier),
                    ),
                    onDecrement = {
                        subtitleOptionsState.setSpeed((subtitleOptionsState.speedMultiplier - 0.05f).coerceAtLeast(0.25f))
                    },
                    onIncrement = {
                        subtitleOptionsState.setSpeed((subtitleOptionsState.speedMultiplier + 0.05f).coerceAtMost(4f))
                    },
                )
            }

            SheetSectionTitle(text = stringResource(R.string.player_section_playback))
            if (isPipSupported) {
                SheetSwitchRow(
                    icon = NextIcons.Pip,
                    title = stringResource(R.string.auto_pip),
                    supportingText = stringResource(R.string.auto_pip_desc),
                    checked = isAutoPipEnabled,
                    onCheckedChange = onAutoPipChanged,
                )
            }
            StepperRow(
                label = stringResource(R.string.controller_timeout),
                value = stringResource(R.string.controls_timeout_short, controllerAutoHideTimeout),
                onDecrement = { onControllerAutoHideTimeoutChanged((controllerAutoHideTimeout - 1).coerceAtLeast(1)) },
                onIncrement = { onControllerAutoHideTimeoutChanged((controllerAutoHideTimeout + 1).coerceAtMost(60)) },
            )
            SheetActionRow(
                icon = NextIcons.Tap,
                title = stringResource(R.string.gestures),
                supportingText = stringResource(R.string.gestures_desc_player),
                onClick = onGesturesClick,
            )
        }
    }
}

@Composable
private fun <T> ChipRow(
    sectionLabel: String,
    selectedLabel: String,
    options: List<T>,
    labelFor: @Composable (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    Column {
        SheetInfoRow(label = sectionLabel, value = selectedLabel)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = isSelected(option),
                    onClick = { onSelect(option) },
                    label = { Text(text = labelFor(option)) },
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = onDecrement) {
            Icon(
                painter = painterResource(R.drawable.ic_remove),
                contentDescription = stringResource(R.string.decrease_value),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
        )
        FilledTonalIconButton(onClick = onIncrement) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.increase_value),
            )
        }
    }
}
