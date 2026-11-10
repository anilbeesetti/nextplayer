package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val stroke = with(LocalDensity.current) {
        Stroke(
            4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    val color = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(
            alpha = .38f,
        )
    }
    Column {
        Slider(
            value = (dragged ?: snapshot.progress).coerceIn(
                0f,
                1f,
            ),
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
            thumb = {
                Box(
                    Modifier.size(16.dp).background(
                        color,
                        CircleShape,
                    ),
                )
            },
            track = { state ->
                LinearWavyProgressIndicator(
                    progress = { state.value },
                    modifier = Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics {},
                    color = color,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .18f),
                    stroke = stroke,
                    trackStroke = stroke,
                    gapSize = 0.dp,
                    stopSize = 0.dp,
                    amplitude = { if (snapshot.playWhenReady) 1f else 0f },
                    wavelength = 20.dp,
                    waveSpeed = if (snapshot.playWhenReady) 20.dp else 0.dp,
                )
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val position = dragged?.let { (it * snapshot.duration.coerceAtLeast(0)).toLong() }
                ?: snapshot.position
            Text(
                Utils.formatDurationMillis(position),
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
