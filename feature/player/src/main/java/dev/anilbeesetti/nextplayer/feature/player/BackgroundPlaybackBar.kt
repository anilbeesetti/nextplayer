package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
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
    artworkModifier: Modifier = Modifier,
    metadataModifier: Modifier = Modifier,
    artworkPainter: Painter = rememberPlaybackArtworkPainter(snapshot),
) {
    if (!snapshot.visible) return
    val title = playbackTitle(snapshot)
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
    val shape = RoundedCornerShape(24.dp)
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
        shadowElevation = 3.dp,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(
                    start = 10.dp,
                    end = 6.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,

            ) {
                PlaybackArtwork(
                    snapshot,
                    Modifier.size(44.dp).testTag("compact_artwork").then(artworkModifier),
                    RoundedCornerShape(8.dp),
                    artworkPainter,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).then(metadataModifier)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                        ),
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
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FilledIconButton(
                        onClick = player::seekToPrevious,
                        enabled =
                            controlsEnabled &&
                                snapshot.commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS),
                        modifier = Modifier.size(36.dp).tvFocusRing(CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            stringResource(R.string.player_controls_previous),
                            Modifier.size(22.dp),
                        )
                    }
                }
                Box(
                    Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PlaybackPlayPauseButton(
                        player,
                        snapshot,
                        Modifier.size(36.dp),
                        controlsEnabled,
                    )
                }
                Box(
                    Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FilledIconButton(
                        onClick = player::seekToNext,
                        enabled =
                            controlsEnabled &&
                                snapshot.commands.contains(Player.COMMAND_SEEK_TO_NEXT),
                        modifier = Modifier.size(36.dp).tvFocusRing(CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            stringResource(R.string.player_controls_next),
                            Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun playbackTitle(snapshot: PlaybackSnapshot): String =
    snapshot.metadata.title?.toString()?.takeIf { it.isNotBlank() }
        ?: snapshot.item?.localConfiguration?.uri?.lastPathSegment
        ?: stringResource(R.string.player_name)

@Composable
internal fun playbackSecondaryText(
    snapshot: PlaybackSnapshot,
    connection: PlaybackConnection,
    deviceName: String?,
): String = when (connection) {
    PlaybackConnection.CONNECTING -> stringResource(
        R.string.connecting_to_device,
        deviceName.orEmpty(),
    )

    PlaybackConnection.LOST -> stringResource(
        R.string.cast_connection_lost,
        deviceName.orEmpty(),
    )

    PlaybackConnection.CAST -> stringResource(
        R.string.casting_on_device,
        deviceName.orEmpty(),
    )

    PlaybackConnection.STOPPED, PlaybackConnection.LOCAL ->
        snapshot.metadata.subtitle?.toString()
            ?: snapshot.item?.localConfiguration?.uri?.path?.substringBeforeLast(
                '/',
            )?.substringAfterLast('/')
            ?: stringResource(R.string.background_play)
}

@Composable
internal fun rememberPlaybackArtworkPainter(snapshot: PlaybackSnapshot): Painter {
    val fallback = painterResource(
        dev.anilbeesetti.nextplayer.feature.player.R.drawable.artwork_default,
    )
    val model = snapshot.metadata.artworkData ?: snapshot.metadata.artworkUri
        ?.takeUnless { it.scheme == "android.resource" }
        ?: snapshot.item?.localConfiguration?.uri?.takeIf {
            it.scheme in setOf(
                "content",
                "file",
            )
        }
        ?: snapshot.metadata.artworkUri
    return rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current).data(model).size(512).build(),
        placeholder = fallback,
        error = fallback,
        fallback = fallback,
    )
}

@Composable
internal fun PlaybackArtwork(
    snapshot: PlaybackSnapshot,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    painter: Painter = rememberPlaybackArtworkPainter(snapshot),
) {
    Surface(
        modifier.clip(shape),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Image(
            painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun PlaybackPlayPauseButton(
    player: Player,
    snapshot: PlaybackSnapshot,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    iconSize: Dp = 22.dp,
    interactionSource: MutableInteractionSource? = null,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
) {
    FilledIconButton(
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
        modifier = modifier.tvFocusRing(shape),
        shape = shape,
        interactionSource = interactionSource,
        colors = colors,
    ) {
        Icon(
            if (snapshot.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (snapshot.playWhenReady) R.string.pause_playback else R.string.play),
            Modifier.size(iconSize),
        )
    }
}
