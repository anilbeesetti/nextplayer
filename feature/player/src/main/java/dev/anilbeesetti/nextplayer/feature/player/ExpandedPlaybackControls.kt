package dev.anilbeesetti.nextplayer.feature.player

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
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
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaylistState

@Composable
internal fun ExpandedPlaybackControls(
    player: Player,
    snapshot: PlaybackSnapshot,
    title: String,
    secondary: String,
    controlsEnabled: Boolean,
    landscape: Boolean,
    connection: PlaybackConnection,
    deviceName: String?,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    onOutput: () -> Unit,
    onStop: () -> Unit,
    onWatchVideo: () -> Unit,
    onCommand: (CustomCommands) -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
    transitionProgress: Float = 1f,
    metadataVisibility: Float = 1f,
    controlsTranslationY: Float = 0f,
    artworkModifier: Modifier = Modifier,
    metadataModifier: Modifier = Modifier,
    artworkPainter: Painter = rememberPlaybackArtworkPainter(snapshot),
) {
    var titleWidth by remember(title) { mutableIntStateOf(0) }
    val collapseFocus = remember { FocusRequester() }
    LaunchedEffect(transitionProgress >= .99f) {
        if (transitionProgress >= .99f) collapseFocus.requestFocus()
    }
    val pane = stringResource(R.string.now_playing)
    val showDevice = connection == PlaybackConnection.CONNECTING || snapshot.isRemote
    // Basic marquee deliberately ignores the platform animation duration scale.
    val motionEnabled = rememberPlaybackMotionEnabled(LocalContext.current.contentResolver)
    val scrollTitle = landscape && controlsEnabled && snapshot.playWhenReady &&
        snapshot.playbackState == Player.STATE_READY && motionEnabled
    val actions: @Composable () -> Unit = {
        PlaybackHeaderActions(
            snapshot,
            connection,
            deviceName,
            controlsEnabled,
            onOutput,
            onQueue,
            showDeviceLabel = !landscape,
        )
    }
    val metadata: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().testTag("playback_metadata")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).testTag("playback_title").then(metadataModifier)) {
                    Text(
                        title,
                        modifier = Modifier.testTag("playback_media_title").then(
                            if (scrollTitle) {
                                Modifier.graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }.drawWithContent {
                                    drawContent()
                                    if (titleWidth > size.width && size.width > 0f) {
                                        val edge = (24.dp.toPx() / size.width).coerceAtMost(.5f)
                                        drawRect(
                                            Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                edge to Color.Black,
                                                1f - edge to Color.Black,
                                                1f to Color.Transparent,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                }.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 2000,
                                    velocity = 25.dp,
                                    spacing = MarqueeSpacing(30.dp),
                                )
                            } else {
                                Modifier
                            },
                        ),
                        onTextLayout = { titleWidth = it.size.width },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = if (landscape) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        secondary,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = if (landscape) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (landscape) {
                    Spacer(Modifier.width(8.dp))
                    actions()
                }
            }
            ConnectionControls(
                connection,
                onCommand,
                enabled = transitionProgress >= .99f,
            )
        }
    }
    val controls: @Composable () -> Unit = {
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = metadataVisibility * ((transitionProgress - .35f) / .65f).coerceIn(
                    0f,
                    1f,
                )
                translationY = controlsTranslationY + 24.dp.toPx() * (1f - transitionProgress)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(vertical = 8.dp)) {
                PlaybackTransportControls(
                    player,
                    snapshot,
                    controlsEnabled,
                )
            }
            Spacer(Modifier.height(14.dp))
            PlaybackModeControls(
                player,
                snapshot,
                controlsEnabled,
                onStop,
                stopEnabled = transitionProgress >= .99f,
            )
            if (connection == PlaybackConnection.LOCAL || connection == PlaybackConnection.CAST) {
                FilledTonalButton(
                    enabled = transitionProgress >= .99f,
                    onClick = {
                        if (snapshot.isRemote) {
                            onCommand(
                                CustomCommands.PLAY_ON_PHONE,
                            )
                        } else {
                            onWatchVideo()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp).tvFocusRing(CircleShape),
                    shape = CircleShape,
                    colors = playbackChipColors(),
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
    BoxWithConstraints(
        modifier.fillMaxSize().testTag("expanded_player")
            .semantics { paneTitle = pane }.safeDrawingPadding(),
    ) {
        val viewportHeight = maxHeight
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BoxWithConstraints(
                        Modifier.aspectRatio(
                            1f,
                            matchHeightConstraintsFirst = true,
                        )
                            .testTag("landscape_artwork_frame"),
                    ) {
                        PlaybackArtworkCarousel(
                            player,
                            snapshot,
                            controlsEnabled,
                            Modifier.fillMaxSize().then(dragModifier).then(artworkModifier),
                            artworkPainter,
                        )
                        PlaybackCollapseButton(
                            onCollapse,
                            Modifier.align(
                                Alignment.TopStart,
                            ).padding(8.dp)
                                .testTag("landscape_collapse")
                                .then(dragModifier).focusRequester(collapseFocus),
                            enabled = transitionProgress >= .99f,
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                        .heightIn(min = viewportHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    metadata()
                    PlaybackSeekControl(
                        player,
                        snapshot,
                        controlsEnabled,
                        modifier = Modifier.graphicsLayer {
                            alpha =
                                metadataVisibility * ((transitionProgress - .35f) / .65f).coerceIn(
                                    0f,
                                    1f,
                                )
                            translationY = controlsTranslationY
                        },
                    )
                    controls()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("playback_header")
                        .then(dragModifier).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaybackCollapseButton(
                        onCollapse,
                        Modifier.focusRequester(collapseFocus),
                        enabled = transitionProgress >= .99f,
                    )
                    Box(Modifier.weight(1f)) {
                        if (!showDevice) {
                            Text(
                                pane,
                                Modifier.padding(
                                    horizontal = 18.dp,
                                ).testTag("playback_header_title"),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    actions()
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight).padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceAround,
                    ) {
                        Box(Modifier.padding(vertical = 8.dp)) {
                            PlaybackArtworkCarousel(
                                player,
                                snapshot,
                                controlsEnabled,
                                Modifier.fillMaxWidth().aspectRatio(1f).then(dragModifier)
                                    .then(artworkModifier),
                                artworkPainter,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            metadata()
                            PlaybackSeekControl(
                                player,
                                snapshot,
                                controlsEnabled,
                                modifier = Modifier.graphicsLayer {
                                    alpha =
                                        metadataVisibility *
                                        ((transitionProgress - .35f) / .65f).coerceIn(
                                            0f,
                                            1f,
                                        )
                                    translationY = controlsTranslationY
                                },
                            )
                        }
                        controls()
                    }
                }
            }
        }
    }
}

@Composable
private fun playbackChipColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f),
    contentColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun PlaybackCollapseButton(
    onCollapse: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = onCollapse,
            enabled = enabled,
            modifier = Modifier.size(42.dp).tvFocusRing(CircleShape),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.onPrimary,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                stringResource(R.string.collapse_player),
            )
        }
    }
}

@Composable
private fun PlaybackHeaderActions(
    snapshot: PlaybackSnapshot,
    connection: PlaybackConnection,
    deviceName: String?,
    enabled: Boolean,
    onOutput: () -> Unit,
    onQueue: () -> Unit,
    showDeviceLabel: Boolean,
) {
    val showDevice = showDeviceLabel &&
        (connection == PlaybackConnection.CONNECTING || snapshot.isRemote)
    val innerCorner by animateDpAsState(
        if (showDevice) 50.dp else 6.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "playback_output_corner",
    )
    val outputShape = RoundedCornerShape(
        topStart = 50.dp,
        bottomStart = 50.dp,
        topEnd = innerCorner.coerceAtLeast(0.dp),
        bottomEnd = innerCorner.coerceAtLeast(0.dp),
    )
    val queueShape = RoundedCornerShape(
        topStart = 6.dp,
        bottomStart = 6.dp,
        topEnd = 50.dp,
        bottomEnd = 50.dp,
    )
    val colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.onPrimary,
        contentColor = MaterialTheme.colorScheme.primary,
    )
    Row(
        Modifier.testTag("playback_output_actions"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onOutput,
                enabled = enabled,
                shape = outputShape,
                color = if (enabled) {
                    colors.containerColor.copy(alpha = .7f)
                } else {
                    colors.disabledContainerColor
                },
                contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
                modifier = Modifier.animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                ).height(42.dp).widthIn(
                    min = 50.dp,
                    max = 190.dp,
                )
                    .testTag("playback_output_action").semantics {
                        role = Role.Button
                    }.tvFocusRing(outputShape),
            ) {
                Row(
                    Modifier.padding(
                        start = 14.dp,
                        end = if (showDevice) 16.dp else 12.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        when {
                            snapshot.isRemote -> Icons.Rounded.CastConnected
                            connection == PlaybackConnection.CONNECTING -> Icons.Rounded.Cast
                            else -> Icons.Rounded.PhoneAndroid
                        },
                        stringResource(R.string.playback_output),
                        Modifier.size(24.dp),
                    )
                    if (showDevice) {
                        Text(
                            if (connection ==
                                PlaybackConnection.CONNECTING
                            ) {
                                stringResource(R.string.connecting)
                            } else {
                                deviceName.orEmpty()
                            },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(
                                1f,
                                fill = false,
                            ),
                        )
                    }
                }
            }
        }
        Box(
            Modifier.size(
                width = 50.dp,
                height = 48.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            FilledIconButton(
                onClick = onQueue,
                enabled = enabled && snapshot.commands.contains(Player.COMMAND_GET_TIMELINE),
                colors = colors,
                shape = queueShape,
                modifier = Modifier.size(
                    width = 50.dp,
                    height = 42.dp,
                )
                    .testTag("playback_queue_action").tvFocusRing(queueShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    stringResource(R.string.now_playing),
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PlaybackArtworkCarousel(
    player: Player,
    snapshot: PlaybackSnapshot,
    controlsEnabled: Boolean,
    modifier: Modifier,
    artworkPainter: Painter,
) {
    val queue = rememberPlaylistState(player)
    val count = queue.mediaItemCount.coerceAtLeast(1)
    val current = queue.currentMediaItemIndex.coerceIn(
        0,
        count - 1,
    )
    val pager = rememberPagerState(
        initialPage = current,
        pageCount = { count },
    )
    var userPaging by remember { mutableStateOf(false) }
    val canSelect = controlsEnabled && (
        snapshot.commands.contains(Player.COMMAND_SEEK_TO_MEDIA_ITEM) ||
            snapshot.commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) ||
            snapshot.commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        )
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) userPaging = true
        }
    }
    LaunchedEffect(
        pager,
        controlsEnabled,
        count,
    ) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && userPaging) {
                userPaging = false
                val target = pager.settledPage
                val playing = player.currentMediaItemIndex
                if (controlsEnabled && target != playing) {
                    when {
                        player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) ->
                            player.seekToDefaultPosition(target)

                        target == playing + 1 &&
                            player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) ->
                            player.seekToNextMediaItem()

                        target == playing - 1 &&
                            player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ->
                            player.seekToPreviousMediaItem()
                    }
                }
                pager.animateScrollToPage(
                    player.currentMediaItemIndex.coerceIn(
                        0,
                        count - 1,
                    ),
                )
            }
        }
    }
    LaunchedEffect(
        current,
        count,
    ) {
        if (!userPaging && current != pager.settledPage) pager.animateScrollToPage(current)
    }
    HorizontalPager(
        state = pager,
        modifier = modifier.testTag("playback_artwork"),
        userScrollEnabled = canSelect && count > 1,
        beyondViewportPageCount = 1,
        key = { index ->
            "$index:${if (queue.mediaItemCount > index) {
                queue.getMediaItemAt(
                    index,
                ).mediaId
            } else {
                ""
            }}"
        },
    ) { index ->
        val item = if (queue.mediaItemCount > index) queue.getMediaItemAt(index) else snapshot.item
        PlaybackArtwork(
            snapshot.copy(
                item = item,
                metadata = if (index == current) {
                    snapshot.metadata
                } else {
                    item?.mediaMetadata ?: snapshot.metadata
                },
            ),
            Modifier.fillMaxSize(),
            RoundedCornerShape(18.dp),
            painter = if (index == current) {
                artworkPainter
            } else {
                rememberPlaybackArtworkPainter(
                    snapshot.copy(
                        item = item,
                        metadata =
                            item?.mediaMetadata ?: snapshot.metadata,
                    ),
                )
            },
        )
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
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playback_play_shape",
    )
    val skipColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
    ButtonGroup(
        modifier = Modifier.fillMaxWidth().height(
            80.dp,
        ).testTag("playback_transport").padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        expandedRatio = .375f,
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
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryFixedDim,
                contentColor = MaterialTheme.colorScheme.onTertiaryFixed,
            ),
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
    stopEnabled: Boolean = true,
) {
    val repeatDescription = stringResource(
        when (snapshot.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.string.loop_mode_one
            Player.REPEAT_MODE_ALL -> R.string.loop_mode_all
            else -> R.string.loop_mode_off
        },
    )
    val colors = IconButtonDefaults.filledIconToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    Surface(
        Modifier.padding(horizontal = 26.dp).padding(bottom = 6.dp).testTag("playback_modes"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = .7f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                enabled = stopEnabled,
                modifier = Modifier.weight(1f).height(48.dp).tvFocusRing(last),
                shape = last,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
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
    enabled: Boolean = true,
) {
    if (connection == PlaybackConnection.CONNECTING) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(32.dp))
            FilledTonalButton(
                enabled = enabled,
                colors = playbackChipColors(),
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
                enabled = enabled,
                colors = playbackChipColors(),
                onClick = {
                    onCommand(CustomCommands.RETRY_CAST)
                },
                modifier = Modifier.tvFocusRing(),
            ) {
                Text(stringResource(R.string.retry))
            }
            FilledTonalButton(
                enabled = enabled,
                colors = playbackChipColors(),
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

internal fun playbackAnimationsEnabled(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f

@Composable
private fun rememberPlaybackMotionEnabled(resolver: ContentResolver): Boolean {
    var enabled by remember(resolver) { mutableStateOf(playbackAnimationsEnabled(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = playbackAnimationsEnabled(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        enabled = playbackAnimationsEnabled(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}
