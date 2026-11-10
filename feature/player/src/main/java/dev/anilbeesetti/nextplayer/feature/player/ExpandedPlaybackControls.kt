package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import kotlin.math.abs

@Composable
internal fun ExpandedPlaybackControls(
    player: Player,
    snapshot: PlaybackSnapshot,
    title: String,
    secondary: String,
    controlsEnabled: Boolean,
    landscape: Boolean,
    connection: PlaybackConnection,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onOutput: () -> Unit,
    onStop: () -> Unit,
    onWatchVideo: () -> Unit,
    onCommand: (CustomCommands) -> Unit,
    dragModifier: Modifier,
) {
    val collapseFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { collapseFocus.requestFocus() }
    val pane = stringResource(R.string.now_playing)
    val buttonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val details: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                secondary,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ConnectionControls(
                connection,
                onCommand,
            )
            Spacer(Modifier.height(12.dp))
            PlaybackSeekControl(
                player,
                snapshot,
                controlsEnabled,
            )
        }
    }
    val controls: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlaybackTransportControls(
                player,
                snapshot,
                controlsEnabled,
            )
            Spacer(Modifier.height(14.dp))
            PlaybackModeControls(
                player,
                snapshot,
                controlsEnabled,
                onStop,
            )
            if (connection == PlaybackConnection.LOCAL || connection == PlaybackConnection.CAST) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        if (snapshot.isRemote) {
                            onCommand(CustomCommands.PLAY_ON_PHONE)
                        } else {
                            onWatchVideo()
                        }
                    },
                    modifier = Modifier.tvFocusRing(CircleShape),
                    shape = CircleShape,
                ) {
                    Icon(
                        if (snapshot.isRemote) {
                            Icons.Rounded.PhoneAndroid
                        } else {
                            Icons.Rounded.SmartDisplay
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(
                            if (snapshot.isRemote) R.string.play_on_phone else R.string.watch_video,
                        ),
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
    Box(
        Modifier.fillMaxSize().testTag("expanded_player")
            .semantics { paneTitle = pane }.safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().testTag("expanded_drag_handle").then(dragModifier)
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onCollapse,
                    colors = buttonColors,
                    modifier = Modifier.size(
                        48.dp,
                    ).focusRequester(collapseFocus).tvFocusRing(CircleShape),
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        stringResource(R.string.collapse_player),
                    )
                }
                Text(
                    pane,
                    Modifier.weight(1f).padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val outputShape = RoundedCornerShape(
                        topStart = 24.dp,
                        bottomStart = 24.dp,
                        topEnd = 6.dp,
                        bottomEnd = 6.dp,
                    )
                    FilledIconButton(
                        onClick = onOutput,
                        enabled = controlsEnabled,
                        colors = buttonColors,
                        shape = outputShape,
                        modifier = Modifier.size(44.dp).tvFocusRing(outputShape),
                    ) {
                        Icon(
                            Icons.Rounded.Cast,
                            stringResource(R.string.playback_output),
                        )
                    }
                    val queueShape = RoundedCornerShape(
                        topStart = 6.dp,
                        bottomStart = 6.dp,
                        topEnd = 24.dp,
                        bottomEnd = 24.dp,
                    )
                    FilledIconButton(
                        onClick = onQueue,
                        enabled =
                            controlsEnabled &&
                                snapshot.commands.contains(Player.COMMAND_GET_TIMELINE),
                        colors = buttonColors,
                        shape = queueShape,
                        modifier = Modifier.size(44.dp).tvFocusRing(queueShape),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            stringResource(R.string.now_playing),
                        )
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (landscape) {
                    Row(
                        Modifier.fillMaxSize().padding(
                            horizontal = 24.dp,
                            vertical = 8.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        PlaybackArtworkCarousel(
                            player,
                            snapshot,
                            controlsEnabled,
                            Modifier.weight(1f).fillMaxHeight().then(dragModifier),
                        )
                        Column(
                            Modifier.weight(
                                1f,
                            ).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            details()
                            controls()
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight).padding(
                                horizontal = 24.dp,
                                vertical = 8.dp,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        PlaybackArtworkCarousel(
                            player,
                            snapshot,
                            controlsEnabled,
                            Modifier.fillMaxWidth(.8f).aspectRatio(1f).then(dragModifier),
                        )
                        details()
                        controls()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackArtworkCarousel(
    player: Player,
    snapshot: PlaybackSnapshot,
    controlsEnabled: Boolean,
    modifier: Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val artworkScale by animateFloatAsState(
        if (snapshot.playWhenReady) 1f else .95f,
        label = "playback_artwork_scale",
    )
    var direction by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier.testTag("playback_artwork").pointerInput(
            player,
            controlsEnabled,
            rtl,
        ) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    drag += amount
                },
                onDragCancel = { drag = 0f },
                onDragEnd = {
                    if (controlsEnabled && abs(drag) > size.width * .2f) {
                        val next = (drag < 0) != rtl
                        direction = if (next) 1f else -1f
                        val command = if (next) {
                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                        } else {
                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                        }
                        if (player.isCommandAvailable(command)) {
                            if (next) {
                                player.seekToNextMediaItem()
                            } else {
                                player.seekToPreviousMediaItem()
                            }
                        }
                    }
                    drag = 0f
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = snapshot.item,
            transitionSpec = {
                (slideInHorizontally { (it * direction).toInt() } + fadeIn()) togetherWith
                    (slideOutHorizontally { (-it * direction).toInt() } + fadeOut())
            },
            label = "playback_artwork",
        ) { item ->
            PlaybackArtwork(
                snapshot.copy(
                    item = item,
                    metadata =
                        item?.mediaMetadata ?: snapshot.metadata,
                ),
                Modifier.aspectRatio(
                    1f,
                    matchHeightConstraintsFirst = true,
                ).graphicsLayer {
                    translationX = drag
                    scaleX = artworkScale
                    scaleY = artworkScale
                },
                RoundedCornerShape(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaybackTransportControls(
    player: Player,
    snapshot: PlaybackSnapshot,
    enabled: Boolean,
) {
    val previous = remember { MutableInteractionSource() }
    val play = remember { MutableInteractionSource() }
    val next = remember { MutableInteractionSource() }
    val playCorner by animateDpAsState(
        if (snapshot.playWhenReady) 26.dp else 40.dp,
        label = "playback_play_shape",
    )
    val skipColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    ButtonGroup(
        modifier = Modifier.fillMaxWidth().height(
            80.dp,
        ).testTag("playback_transport").padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        expandedRatio = .2f,
    ) {
        FilledIconButton(
            onClick = player::seekToPrevious,
            enabled = enabled && snapshot.commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS),
            modifier = Modifier.weight(
                1f,
            ).fillMaxHeight().animateWidth(previous).tvFocusRing(CircleShape),
            shape = CircleShape,
            colors = skipColors,
            interactionSource = previous,
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                stringResource(R.string.player_controls_previous),
                Modifier.size(32.dp),
            )
        }
        PlaybackPlayPauseButton(
            player,
            snapshot,
            Modifier.weight(1f).fillMaxHeight().animateWidth(play),
            enabled,
            RoundedCornerShape(playCorner),
            36.dp,
            play,
        )
        FilledIconButton(
            onClick = player::seekToNext,
            enabled = enabled && snapshot.commands.contains(Player.COMMAND_SEEK_TO_NEXT),
            modifier = Modifier.weight(
                1f,
            ).fillMaxHeight().animateWidth(next).tvFocusRing(CircleShape),
            shape = CircleShape,
            colors = skipColors,
            interactionSource = next,
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                stringResource(R.string.player_controls_next),
                Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun PlaybackModeControls(
    player: Player,
    snapshot: PlaybackSnapshot,
    enabled: Boolean,
    onStop: () -> Unit,
) {
    val repeatDescription = stringResource(
        when (snapshot.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.string.loop_mode_one
            Player.REPEAT_MODE_ALL -> R.string.loop_mode_all
            else -> R.string.loop_mode_off
        },
    )
    val colors = IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .1f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Surface(
        Modifier.padding(horizontal = 26.dp).testTag("playback_modes"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val first = RoundedCornerShape(
                topStart = 30.dp,
                bottomStart = 30.dp,
                topEnd = 8.dp,
                bottomEnd = 8.dp,
            )
            FilledIconToggleButton(
                checked = snapshot.shuffle,
                onCheckedChange = {
                    player.shuffleModeEnabled = it
                },
                enabled = enabled && snapshot.commands.contains(Player.COMMAND_SET_SHUFFLE_MODE),
                modifier = Modifier.weight(1f).height(48.dp).tvFocusRing(first),
                shape = first,
                colors = colors,
            ) {
                Icon(
                    Icons.Rounded.Shuffle,
                    stringResource(R.string.player_controls_shuffle),
                )
            }
            val middle = RoundedCornerShape(8.dp)
            FilledIconToggleButton(
                checked = snapshot.repeatMode != Player.REPEAT_MODE_OFF,
                onCheckedChange = { player.repeatMode = (player.repeatMode + 1) % 3 },
                enabled = enabled && snapshot.commands.contains(Player.COMMAND_SET_REPEAT_MODE),
                modifier = Modifier.weight(1f).height(48.dp).semantics {
                    stateDescription = repeatDescription
                }.tvFocusRing(middle),
                shape = middle,
                colors = colors,
            ) {
                Icon(
                    if (snapshot.repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    stringResource(R.string.player_controls_repeat),
                )
            }
            val last = RoundedCornerShape(
                topStart = 8.dp,
                bottomStart = 8.dp,
                topEnd = 30.dp,
                bottomEnd = 30.dp,
            )
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(48.dp).tvFocusRing(last),
                shape = last,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .1f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    Icons.Rounded.Stop,
                    stringResource(R.string.stop_player_session),
                )
            }
        }
    }
}

@Composable
private fun ConnectionControls(
    connection: PlaybackConnection,
    onCommand: (CustomCommands) -> Unit,
) {
    if (connection == PlaybackConnection.CONNECTING) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(32.dp))
            FilledTonalButton(
                onClick = {
                    onCommand(CustomCommands.CANCEL_CAST)
                },
                modifier = Modifier.tvFocusRing(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    } else if (connection == PlaybackConnection.LOST) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    onCommand(CustomCommands.RETRY_CAST)
                },
                modifier = Modifier.tvFocusRing(),
            ) {
                Text(stringResource(R.string.retry))
            }
            FilledTonalButton(
                onClick = {
                    onCommand(CustomCommands.PLAY_ON_PHONE)
                },
                modifier = Modifier.tvFocusRing(),
            ) {
                Text(stringResource(R.string.play_on_phone))
            }
        }
    }
}
