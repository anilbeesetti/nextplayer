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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaylistState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    interactive: Boolean = true,
    transitionProgress: () -> Float = { 1f },
    controlsTranslationY: () -> Float = { 0f },
    artworkModifier: Modifier = Modifier,
    metadataModifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    queueSheet: PlaybackQueueSheet? = null,
) {
    var titleWidth by remember(title) { mutableIntStateOf(0) }
    val collapseFocus = remember { FocusRequester() }
    LaunchedEffect(interactive) {
        if (interactive) collapseFocus.requestFocus()
    }
    val pane = stringResource(R.string.now_playing)
    val showDevice = connection == PlaybackConnection.CONNECTING || snapshot.isRemote
    // Basic marquee deliberately ignores the platform animation duration scale.
    val motionEnabled = rememberPlaybackMotionEnabled(LocalContext.current.contentResolver)
    val scrollTitle = landscape && controlsEnabled && snapshot.playWhenReady &&
        snapshot.playbackState == Player.STATE_READY && motionEnabled
    val actions: @Composable () -> Unit = {
        Box(Modifier.blockTouchesUntil(interactive)) {
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
                        maxLines = 1,
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
        }
    }
    val controls: @Composable (transportModifier: Modifier, modesModifier: Modifier) -> Unit = {
            transportModifier,
            modesModifier,
        ->
        Column(
            Modifier.blockTouchesUntil(interactive),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                transportModifier.padding(vertical = 8.dp).expansionReveal(
                    .45f,
                    transitionProgress,
                    controlsTranslationY,
                ),
            ) {
                PlaybackTransportControls(
                    player,
                    snapshot,
                    controlsEnabled,
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modesModifier.expansionReveal(
                    .55f,
                    transitionProgress,
                    controlsTranslationY,
                ),
            ) {
                PlaybackModeControls(
                    player,
                    snapshot,
                    controlsEnabled,
                    onStop,
                )
            }
        }
    }
    // Rests on the artwork, so it fades in only once the artwork has landed.
    val artworkOverlayAlpha by animateFloatAsState(
        if (interactive) 1f else 0f,
        tween(200),
        label = "artwork_overlay_alpha",
    )
    // Connection states sit on the artwork, so nothing below moves as the connection changes.
    val artworkOverlay: @Composable BoxScope.() -> Unit = {
        if (connection == PlaybackConnection.CONNECTING || connection == PlaybackConnection.LOST) {
            Box(
                Modifier.matchParentSize().graphicsLayer { alpha = artworkOverlayAlpha }
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .32f)),
            )
            Column(
                Modifier.align(Alignment.Center).graphicsLayer { alpha = artworkOverlayAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val buttons = Modifier.blockTouchesUntil(interactive).tvFocusRing()
                if (connection == PlaybackConnection.CONNECTING) {
                    ContainedLoadingIndicator(Modifier.testTag("cast_connecting"))
                    FilledTonalButton(
                        onClick = { onCommand(CustomCommands.CANCEL_CAST) },
                        modifier = buttons,
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                } else {
                    FilledTonalButton(
                        onClick = { onCommand(CustomCommands.RETRY_CAST) },
                        modifier = buttons,
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                    FilledTonalButton(
                        onClick = { onCommand(CustomCommands.PLAY_ON_PHONE) },
                        modifier = buttons,
                    ) {
                        Text(stringResource(R.string.play_on_phone))
                    }
                }
            }
        } else if (connection == PlaybackConnection.LOCAL ||
            connection == PlaybackConnection.CAST
        ) {
            FilledTonalButton(
                onClick = {
                    if (snapshot.isRemote) {
                        onCommand(
                            CustomCommands.PLAY_ON_PHONE,
                        )
                    } else {
                        onWatchVideo()
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    .graphicsLayer { alpha = artworkOverlayAlpha }
                    .blockTouchesUntil(interactive).tvFocusRing(CircleShape),
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
    BoxWithConstraints(
        modifier.fillMaxSize().testTag("expanded_player")
            .semantics { paneTitle = pane }.safeDrawingPadding(),
    ) {
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)) {
                    val startGutter = 24.dp
                    val artworkSize = minOf(
                        maxWidth - startGutter,
                        maxHeight,
                    )
                    val endInset = (maxWidth - startGutter - artworkSize) / 2
                    val startInset = startGutter + endInset
                    PlaybackArtworkCarousel(
                        player,
                        snapshot,
                        controlsEnabled && interactive,
                        Modifier.fillMaxWidth().height(artworkSize).align(Alignment.CenterStart)
                            .then(dragModifier),
                        artworkSize,
                        PaddingValues(
                            start = startInset,
                            end = endInset,
                        ),
                        pageSpacing = startInset * 2,
                        artworkModifier,
                    )
                    Box(
                        Modifier.align(Alignment.CenterStart).padding(start = startInset)
                            .size(artworkSize).testTag("landscape_artwork_frame"),
                    ) {
                        PlaybackCollapseButton(
                            onCollapse,
                            Modifier.align(
                                Alignment.TopStart,
                            ).padding(8.dp)
                                .testTag("landscape_collapse")
                                .then(dragModifier).focusRequester(collapseFocus)
                                .blockTouchesUntil(interactive),
                        )
                        artworkOverlay()
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    metadata()
                    PlaybackSeekControl(
                        player,
                        snapshot,
                        controlsEnabled,
                        modifier = Modifier.blockTouchesUntil(interactive).expansionReveal(
                            .35f,
                            transitionProgress,
                            controlsTranslationY,
                        ),
                    )
                    controls(
                        Modifier,
                        Modifier,
                    )
                }
            }
        } else {
            val sheet = queueSheet
            val queueProgress: () -> Float = {
                sheet?.progress?.coerceIn(
                    -.15f,
                    2.15f,
                ) ?: 0f
            }
            val queueShown by remember(sheet) { derivedStateOf { sheet?.engaged == true } }
            // The mini row only exists once it shows, so it never takes the header's taps.
            val miniRowShown by remember(sheet) {
                derivedStateOf { (sheet?.progress ?: 0f) > 1.35f }
            }
            var container by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var artworkBounds by remember { mutableStateOf(Rect.Zero) }
            var metadataBounds by remember { mutableStateOf(Rect.Zero) }
            var transportBounds by remember { mutableStateOf(Rect.Zero) }
            val measured: (onBounds: (Rect) -> Unit) -> Modifier = { onBounds ->
                Modifier.onGloballyPositioned { coordinates ->
                    container?.takeIf { it.isAttached }?.let {
                        onBounds(
                            it.localBoundingBoxOf(
                                coordinates,
                                clipBounds = false,
                            ),
                        )
                    }
                }
            }
            val density = LocalDensity.current
            val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
            val bottomInset = WindowInsets.safeDrawing.getBottom(density)
            BoxWithConstraints(Modifier.fillMaxSize().onPlaced { container = it }) {
                val stages = remember(
                    constraints.maxWidth,
                    constraints.maxHeight,
                    topInset,
                    metadataBounds.bottom,
                    transportBounds.bottom,
                    density,
                ) {
                    QueueStageGeometry(
                        constraints.maxWidth.toFloat(),
                        constraints.maxHeight.toFloat(),
                        topInset,
                        // From the title's bottom to the transport's, the block that rides up.
                        blockHeight = maxOf(
                            transportBounds.bottom - metadataBounds.bottom,
                            0f,
                        ),
                        density,
                    )
                }
                if (sheet != null) {
                    LaunchedEffect(
                        sheet,
                        stages,
                    ) {
                        sheet.setAnchors(
                            closed = constraints.maxHeight.toFloat(),
                            half = stages.halfPanelTop,
                            full = stages.queuePanelTop,
                        )
                    }
                }
                val artworkAt: (Float) -> Rect = { progress ->
                    if (progress <= 1f) {
                        lerp(
                            artworkBounds,
                            stages.halfArtwork,
                            progress,
                        )
                    } else {
                        lerp(
                            stages.halfArtwork,
                            stages.thumbnail,
                            progress - 1f,
                        )
                    }
                }
                val metadataShift: (Float) -> Float = { progress ->
                    val half = stages.halfArtwork.bottom - stages.gap * 2 - metadataBounds.bottom
                    if (progress <= 1f) {
                        half * progress
                    } else {
                        half + artworkAt(progress).bottom - stages.halfArtwork.bottom
                    }
                }
                // Seek bar and transport keep their spacing under the title as it rides up.
                val blockShift = metadataShift
                if (queueShown) {
                    QueueArtworkBanner(
                        snapshot,
                        bounds = { artworkAt(queueProgress()) },
                        progress = queueProgress,
                        modifier = dragModifier,
                    )
                }
                // A vertical swipe anywhere on the player moves it; the pager and slider keep theirs.
                Column(Modifier.fillMaxSize().then(dragModifier)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("playback_header")
                            .then(headerModifier).graphicsLayer {
                                val progress = queueProgress()
                                alpha = 1f - smoothStep(
                                    1f,
                                    1.35f,
                                    progress,
                                )
                                translationY = -24.dp.toPx() * smoothStep(
                                    1f,
                                    1.6f,
                                    progress,
                                )
                            }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaybackCollapseButton(
                            onCollapse,
                            Modifier.focusRequester(collapseFocus).blockTouchesUntil(interactive),
                        )
                        Box(Modifier.weight(1f)) {
                            if (!showDevice) {
                                Text(
                                    pane,
                                    Modifier.padding(
                                        horizontal = 18.dp,
                                    ).testTag("playback_header_title").graphicsLayer {
                                        alpha = 1f - smoothStep(
                                            .15f,
                                            .6f,
                                            queueProgress(),
                                        )
                                    },
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
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceAround,
                    ) {
                        // Like a fixed full player, nothing scrolls: the artwork gives up height first.
                        BoxWithConstraints(
                            Modifier.weight(
                                1f,
                                fill = false,
                            ).fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            val artworkSize = minOf(
                                maxWidth - 48.dp,
                                maxHeight,
                            ).coerceAtLeast(0.dp)
                            val side = (maxWidth - artworkSize) / 2
                            PlaybackArtworkCarousel(
                                player,
                                snapshot,
                                controlsEnabled && interactive && !queueShown,
                                Modifier.fillMaxWidth().height(artworkSize)
                                    .graphicsLayer { alpha = if (queueShown) 0f else 1f },
                                artworkSize,
                                PaddingValues(horizontal = side),
                                pageSpacing = side * 2,
                                measured { artworkBounds = it }.then(artworkModifier),
                            )
                            Box(
                                Modifier.size(artworkSize).align(Alignment.Center).graphicsLayer {
                                    alpha = 1f - smoothStep(
                                        0f,
                                        .3f,
                                        queueProgress(),
                                    )
                                },
                            ) { artworkOverlay() }
                        }
                        Column(
                            Modifier.padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                measured { metadataBounds = it }.graphicsLayer {
                                    val progress = queueProgress()
                                    translationY = metadataShift(progress)
                                    alpha = 1f - smoothStep(
                                        1f,
                                        1.4f,
                                        progress,
                                    )
                                },
                            ) { metadata() }
                            PlaybackSeekControl(
                                player,
                                snapshot,
                                controlsEnabled,
                                modifier = Modifier.graphicsLayer {
                                    val progress = queueProgress()
                                    translationY = blockShift(progress)
                                    alpha = 1f - smoothStep(
                                        1f,
                                        1.3f,
                                        progress,
                                    )
                                }.blockTouchesUntil(interactive).expansionReveal(
                                    .35f,
                                    transitionProgress,
                                    controlsTranslationY,
                                ),
                            )
                        }
                        Box(Modifier.padding(horizontal = 24.dp)) {
                            controls(
                                measured { transportBounds = it }.graphicsLayer {
                                    val progress = queueProgress()
                                    translationY = blockShift(progress)
                                    alpha = 1f - smoothStep(
                                        1f,
                                        1.3f,
                                        progress,
                                    )
                                },
                                Modifier.graphicsLayer {
                                    val progress = queueProgress()
                                    translationY = blockShift(progress)
                                    alpha = 1f - smoothStep(
                                        0f,
                                        .4f,
                                        progress,
                                    )
                                },
                            )
                        }
                    }
                }
                if (sheet != null && miniRowShown) {
                    QueueMiniRow(
                        player,
                        snapshot,
                        title,
                        secondary,
                        controlsEnabled,
                        progress = queueProgress,
                        onClick = { sheet.animateTo(QueueStage.HALF) },
                        modifier = Modifier.height(stages.rowHeightDp).then(dragModifier),
                    )
                }
                if (sheet != null && queueShown) {
                    QueuePanel(
                        player,
                        sheet,
                        bottomInset = with(density) { bottomInset.toDp() },
                    )
                }
            }
        }
    }
}

/** Keeps a control looking normal while the player moves, but ignores touches until it settles. */
private fun Modifier.blockTouchesUntil(interactive: Boolean): Modifier = pointerInput(interactive) {
    if (interactive) return@pointerInput
    awaitEachGesture {
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/** Fades a control in behind the artwork, starting at [start] of the transition. */
private fun Modifier.expansionReveal(
    start: Float,
    progress: () -> Float,
    offset: () -> Float = { 0f },
    distance: Dp = 20.dp,
): Modifier = graphicsLayer {
    val reveal = ((progress() - start) / (1f - start)).coerceIn(
        0f,
        1f,
    )
    alpha = reveal
    translationY = offset() + distance.toPx() * (1f - reveal)
}

@Composable
private fun playbackChipColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f),
    contentColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun PlaybackCollapseButton(onCollapse: () -> Unit, modifier: Modifier) {
    Box(
        modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = onCollapse,
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
                color = if (enabled) colors.containerColor else colors.disabledContainerColor,
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
    artworkSize: Dp,
    contentPadding: PaddingValues,
    pageSpacing: Dp,
    artworkModifier: Modifier,
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
        modifier = modifier.testTag("playback_artwork_pager"),
        contentPadding = contentPadding,
        pageSize = PageSize.Fixed(artworkSize),
        pageSpacing = pageSpacing,
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
        // The current page shows what plays: a receiver's queue position can lag its playback.
        val item = if (index == current || queue.mediaItemCount <= index) {
            snapshot.item
        } else {
            queue.getMediaItemAt(index)
        }
        PlaybackArtwork(
            snapshot.copy(
                item = item,
                metadata = if (index == current) {
                    snapshot.metadata
                } else {
                    item?.mediaMetadata ?: snapshot.metadata
                },
            ),
            Modifier.fillMaxSize().then(
                if (index == current) {
                    Modifier.testTag("playback_artwork").then(artworkModifier)
                } else {
                    Modifier
                },
            ),
            RoundedCornerShape(18.dp),
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
    val skipColors = playbackSkipColors()
    ButtonGroup(
        overflowIndicator = {},
        modifier = Modifier.fillMaxWidth().height(
            80.dp,
        ).testTag("playback_transport").padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        expandedRatio = .375f,
    ) {
        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = player::seekToPrevious,
                    enabled = enabled &&
                        snapshot.commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS),
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
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                PlaybackPlayPauseButton(
                    player,
                    snapshot,
                    Modifier.weight(1f).fillMaxHeight().animateWidth(play),
                    enabled,
                    RoundedCornerShape(playCorner),
                    36.dp,
                    play,
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
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
            },
            menuContent = {},
        )
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
            val first = ButtonGroupDefaults.connectedLeadingButtonShape
            FilledIconToggleButton(
                checked = snapshot.shuffle,
                onCheckedChange = {
                    player.shuffleModeEnabled = it
                },
                enabled = enabled && snapshot.commands.contains(Player.COMMAND_SET_SHUFFLE_MODE),
                modifier = Modifier.weight(1f).height(48.dp).tvFocusRing(first),
                shapes = IconToggleButtonShapes(
                    shape = first,
                    pressedShape = ButtonGroupDefaults.connectedLeadingButtonPressShape,
                    checkedShape = ButtonGroupDefaults.connectedButtonCheckedShape,
                ),
                colors = colors,
            ) {
                Icon(
                    Icons.Rounded.Shuffle,
                    stringResource(R.string.player_controls_shuffle),
                )
            }
            val middle = ShapeDefaults.Small
            FilledIconToggleButton(
                checked = snapshot.repeatMode != Player.REPEAT_MODE_OFF,
                onCheckedChange = { player.repeatMode = (player.repeatMode + 1) % 3 },
                enabled = enabled && snapshot.commands.contains(Player.COMMAND_SET_REPEAT_MODE),
                modifier = Modifier.weight(1f).height(48.dp).semantics {
                    stateDescription = repeatDescription
                }.tvFocusRing(middle),
                shapes = IconToggleButtonShapes(
                    shape = middle,
                    pressedShape = ButtonGroupDefaults.connectedMiddleButtonPressShape,
                    checkedShape = ButtonGroupDefaults.connectedButtonCheckedShape,
                ),
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
            val last = ButtonGroupDefaults.connectedTrailingButtonShape
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(48.dp).tvFocusRing(last),
                shapes = IconButtonShapes(
                    shape = last,
                    pressedShape = ButtonGroupDefaults.connectedTrailingButtonPressShape,
                ),
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
