package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun PlaybackSeekControl(
    player: Player,
    snapshot: PlaybackSnapshot,
    controlsEnabled: Boolean = true,
) {
    var dragged by remember(snapshot.item?.mediaId) { mutableStateOf<Float?>(null) }
    val enabled = controlsEnabled && snapshot.duration > 0 &&
        snapshot.commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    val label = stringResource(R.string.player_controls_progress)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragging by interaction.collectIsDraggedAsState()
    val seeking = pressed || dragging || dragged != null
    val thumbWidth by animateDpAsState(
        if (seeking) 6.dp else 16.dp,
        animationSpec = tween(
            250,
            easing = FastOutSlowInEasing,
        ),
        label = "seek_thumb_width",
    )
    val thumbHeight by animateDpAsState(
        if (seeking) 24.dp else 16.dp,
        animationSpec = tween(
            250,
            easing = FastOutSlowInEasing,
        ),
        label = "seek_thumb_height",
    )
    val waving = enabled && snapshot.playWhenReady &&
        snapshot.playbackState == Player.STATE_READY && !seeking
    val stroke = with(LocalDensity.current) {
        Stroke(
            5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    val color = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(
            alpha = .38f,
        )
    }
    val value = (dragged ?: snapshot.progress).coerceIn(
        0f,
        1f,
    )
    Column(Modifier.heightIn(min = 70.dp)) {
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().height(24.dp).testTag("playback_seek_track"),
                contentAlignment = Alignment.Center,
            ) {
                LinearWavyProgressIndicator(
                    progress = { value },
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    color = color,
                    trackColor = playbackInactiveTrackColor(
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    stroke = stroke,
                    trackStroke = stroke,
                    gapSize = when {
                        seeking -> 8.4.dp
                        waving -> 14.dp
                        else -> 12.dp
                    },
                    stopSize = 3.dp,
                    amplitude = { if (waving) 1f else 0f },
                    wavelength = 40.dp,
                    waveSpeed = if (waving) 20.dp else 0.dp,
                )
                Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                    val width = thumbWidth.toPx()
                    val height = thumbHeight.toPx()
                    val center = playbackThumbCenter(
                        value,
                        size.width,
                        width,
                        layoutDirection == LayoutDirection.Rtl,
                    )
                    drawRoundRect(
                        color,
                        topLeft = Offset(
                            center - width / 2,
                            (size.height - height) / 2,
                        ),
                        size = Size(
                            width,
                            height,
                        ),
                        cornerRadius = CornerRadius(width / 2),
                    )
                }
            }
            Slider(
                value = value,
                onValueChange = { dragged = it },
                onValueChangeFinished = {
                    dragged?.let {
                        if (enabled) {
                            player.seekTo(
                                (it * snapshot.duration).toLong().coerceIn(
                                    0,
                                    snapshot.duration,
                                ),
                            )
                        }
                    }
                    dragged = null
                },
                enabled = enabled,
                interactionSource = interaction,
                thumb = {
                    Spacer(
                        Modifier.size(
                            0.dp,
                            24.dp,
                        ),
                    )
                },
                // Native thumb insets must not resize or move the decorative track.
                track = { Spacer(Modifier.fillMaxWidth().height(24.dp)) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val position = dragged?.let { (it * snapshot.duration.coerceAtLeast(0)).toLong() }
                ?: snapshot.position
            Text(
                Utils.formatDurationMillis(position),
                modifier = Modifier.testTag("playback_position"),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                if (snapshot.duration > 0) Utils.formatDurationMillis(snapshot.duration) else "—",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
internal fun PlaybackVolumeControl(player: Player, snapshot: PlaybackSnapshot) {
    if (!snapshot.isRemote) {
        if (snapshot.commands.contains(Player.COMMAND_SET_VOLUME)) {
            val label = stringResource(R.string.volume)
            Text(label)
            Slider(
                value = snapshot.volume.coerceIn(
                    0f,
                    1f,
                ),
                onValueChange = { player.volume = it },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
        return
    }
    val maximum = snapshot.deviceInfo.maxVolume
    if (maximum <= 0 ||
        !snapshot.commands.contains(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
    ) {
        return
    }
    var dragged by remember(player) { mutableStateOf<Float?>(null) }
    val label = stringResource(R.string.cast_volume)
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = (dragged ?: snapshot.deviceVolume.toFloat()).coerceIn(
                0f,
                maximum.toFloat(),
            ),
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let {
                    player.setDeviceVolume(
                        it.toInt().coerceIn(
                            0,
                            maximum,
                        ),
                        0,
                    )
                }
                dragged = null
            },
            valueRange = 0f..maximum.toFloat(),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

internal fun playbackInactiveTrackColor(onContainer: Color): Color = onContainer.copy(alpha = .7f)

internal fun playbackThumbCenter(
    progress: Float,
    trackWidth: Float,
    thumbWidth: Float,
    rtl: Boolean,
): Float {
    val inset = (thumbWidth / 2).coerceAtMost(trackWidth / 2)
    val fraction = if (rtl) 1f - progress else progress
    return (fraction * trackWidth).coerceIn(
        inset,
        trackWidth - inset,
    )
}
