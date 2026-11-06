package dev.anilbeesetti.nextplayer.feature.player

import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.cast.castDeviceName
import dev.anilbeesetti.nextplayer.feature.player.cast.playOnPhone
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.state.rememberDeviceInfo
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaController
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaybackSnapshot

@OptIn(UnstableApi::class)
@Composable
fun BackgroundPlaybackBar(
    modifier: Modifier = Modifier,
    onVisibilityChanged: (Boolean) -> Unit = {},
) {
    val controller = rememberMediaController(ownsVideoSurface = false)
    val context = LocalContext.current
    if (controller == null) {
        LaunchedEffect(Unit) { onVisibilityChanged(false) }
        return
    }
    val deviceInfo = rememberDeviceInfo(controller)
    MiniPlayer(
        player = controller,
        modifier = modifier,
        deviceName = if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            castDeviceName(context)
        } else {
            null
        },
        onOpenPlayer = {
            try {
                controller.sessionActivity?.send()
            } catch (_: PendingIntent.CanceledException) {
                // The session ended between the tap and delivery.
            }
        },
        onStop = controller::stopPlayerSession,
        onPlayOnPhone = { playOnPhone(context) },
        onVisibilityChanged = onVisibilityChanged,
    )
}

@Composable
internal fun MiniPlayer(
    player: Player,
    onOpenPlayer: () -> Unit,
    onStop: () -> Unit,
    onPlayOnPhone: () -> Unit,
    modifier: Modifier = Modifier,
    deviceName: String? = null,
    onVisibilityChanged: (Boolean) -> Unit = {},
) {
    val snapshot = rememberPlaybackSnapshot(player)
    LaunchedEffect(snapshot.visible) { onVisibilityChanged(snapshot.visible) }
    DisposableEffect(Unit) { onDispose { onVisibilityChanged(false) } }
    var showCastControls by remember(player) { mutableStateOf(false) }
    LaunchedEffect(
        snapshot.isRemote,
        snapshot.visible,
    ) {
        if (!snapshot.isRemote || !snapshot.visible) showCastControls = false
    }
    if (!snapshot.visible) return
    val title = snapshot.metadata.title?.toString()?.takeIf { it.isNotBlank() }
        ?: snapshot.item?.localConfiguration?.uri?.lastPathSegment
        ?: stringResource(R.string.player_name)
    val output = if (snapshot.isRemote) {
        deviceName?.let {
            stringResource(
                R.string.casting_on_device,
                it,
            )
        }
            ?: stringResource(R.string.casting_video)
    } else {
        stringResource(R.string.background_play)
    }
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = { if (snapshot.isRemote) showCastControls = true else onOpenPlayer() },
        modifier = modifier
            .testTag("mini_player")
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
                        if (snapshot.isRemote) {
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
                MiniPlayPauseButton(
                    player,
                    snapshot,
                )
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.tvFocusRing(),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        stringResource(R.string.stop_player_session),
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
    if (showCastControls && snapshot.isRemote) {
        CastControlsSheet(
            player,
            snapshot,
            title,
            output,
            onDismiss = {
                showCastControls = false
            },
            onPlayOnPhone = onPlayOnPhone,
            onStop = onStop,
        )
    }
}

@Composable
internal fun PlaybackArtwork(snapshot: PlaybackSnapshot, modifier: Modifier = Modifier) {
    Surface(
        modifier.clip(RoundedCornerShape(12.dp)),
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
        enabled = snapshot.commands.contains(Player.COMMAND_PLAY_PAUSE),
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
