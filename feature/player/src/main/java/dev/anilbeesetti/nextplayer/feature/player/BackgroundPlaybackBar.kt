package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaybackSnapshot

@Composable
internal fun MiniPlayer(
    player: Player,
    onOpenPlayer: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    deviceName: String? = null,
    controlsEnabled: Boolean = true,
    snapshot: PlaybackSnapshot = rememberPlaybackSnapshot(player),
    secondaryText: String? = null,
    connecting: Boolean = false,
) {
    if (!snapshot.visible) return
    val title = snapshot.metadata.title?.toString()?.takeIf { it.isNotBlank() }
        ?: snapshot.item?.localConfiguration?.uri?.lastPathSegment
        ?: stringResource(R.string.player_name)
    val output = secondaryText ?: if (snapshot.isRemote) {
        deviceName?.let {
            stringResource(
                R.string.casting_on_device,
                it,
            )
        }
            ?: stringResource(R.string.casting_video)
    } else {
        snapshot.metadata.subtitle?.toString()?.takeIf { it.isNotBlank() }
            ?: snapshot.item?.localConfiguration?.uri?.path?.substringBeforeLast(
                '/',
            )?.substringAfterLast('/')
            ?: stringResource(R.string.background_play)
    }
    val stopLabel = stringResource(R.string.stop_player_session)
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onOpenPlayer,
        modifier = modifier
            .testTag("mini_player")
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(stopLabel) {
                        onStop()
                        true
                    },
                )
            }
            .tvFocusRing(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    start = 12.dp,
                    end = 4.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaybackArtwork(
                    snapshot,
                    Modifier.size(48.dp),
                    CircleShape,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (connecting) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else if (snapshot.isRemote) {
                            Icon(
                                Icons.Outlined.CastConnected,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            output,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = player::seekToPrevious,
                    enabled =
                        controlsEnabled &&
                            snapshot.commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS),
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
                    enabled = controlsEnabled,
                )
                IconButton(
                    onClick = player::seekToNext,
                    enabled =
                        controlsEnabled && snapshot.commands.contains(Player.COMMAND_SEEK_TO_NEXT),
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_skip_next),
                        stringResource(R.string.player_controls_next),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { snapshot.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
internal fun PlaybackArtwork(
    snapshot: PlaybackSnapshot,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Surface(
        modifier.clip(shape),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = snapshot.metadata.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(
                    dev.anilbeesetti.nextplayer.feature.player.R.drawable.artwork_default,
                ),
                error = painterResource(
                    dev.anilbeesetti.nextplayer.feature.player.R.drawable.artwork_default,
                ),
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
internal fun MiniPlayPauseButton(
    player: Player,
    snapshot: PlaybackSnapshot,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = {
            if (snapshot.playWhenReady) {
                player.pause()
            } else {
                if (snapshot.playbackState == Player.STATE_IDLE &&
                    player.isCommandAvailable(Player.COMMAND_PREPARE)
                ) {
                    player.prepare()
                }
                player.play()
            }
        },
        enabled = enabled && snapshot.commands.contains(Player.COMMAND_PLAY_PAUSE),
        modifier = modifier.tvFocusRing(),
    ) {
        Icon(
            painterResource(
                if (snapshot.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play,
            ),
            stringResource(if (snapshot.playWhenReady) R.string.pause_playback else R.string.play),
        )
    }
}
