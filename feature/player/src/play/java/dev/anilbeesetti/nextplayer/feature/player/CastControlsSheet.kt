package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CastControlsSheet(
    player: Player,
    snapshot: PlaybackSnapshot,
    title: String,
    output: String,
    onDismiss: () -> Unit,
    onPlayOnPhone: () -> Unit,
    onStop: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("cast_controls"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.CastConnected,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    output,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlaybackArtwork(
                snapshot,
                Modifier.size(160.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            CastSeekControl(
                player,
                snapshot,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                IconButton(
                    onClick = player::seekToPrevious,
                    enabled = snapshot.commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS),
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_skip_prev),
                        stringResource(R.string.player_controls_previous),
                    )
                }
                MiniPlayPauseButton(
                    player,
                    snapshot,
                    Modifier.size(64.dp),
                )
                IconButton(
                    onClick = player::seekToNext,
                    enabled = snapshot.commands.contains(Player.COMMAND_SEEK_TO_NEXT),
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_skip_next),
                        stringResource(R.string.player_controls_next),
                    )
                }
            }
            CastVolumeControl(
                player,
                snapshot,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onPlayOnPhone,
                    modifier = Modifier.weight(1f).tvFocusRing(),
                ) {
                    Text(stringResource(R.string.play_on_phone))
                }
                TextButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f).tvFocusRing(),
                ) {
                    Text(stringResource(R.string.stop_player_session))
                }
            }
        }
    }
}

@Composable
private fun CastSeekControl(player: Player, snapshot: PlaybackSnapshot) {
    var dragged by remember(snapshot.item?.mediaId) { mutableStateOf<Float?>(null) }
    val enabled = snapshot.duration > 0 &&
        snapshot.commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    val label = stringResource(R.string.player_controls_progress)
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
private fun CastVolumeControl(player: Player, snapshot: PlaybackSnapshot) {
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
