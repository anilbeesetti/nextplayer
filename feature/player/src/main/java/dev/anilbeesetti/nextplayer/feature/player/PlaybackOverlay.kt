package dev.anilbeesetti.nextplayer.feature.player

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.cast.CastButton
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.EXTRA_WATCH_VIDEO
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.service.playbackCommand
import dev.anilbeesetti.nextplayer.feature.player.service.setAudioOutput
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaController
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.ui.PlaybackDismissUndoBar
import dev.anilbeesetti.nextplayer.feature.player.ui.PlaybackQueueContent
import dev.anilbeesetti.nextplayer.feature.player.ui.PlaybackSwipeToDismiss
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaybackOverlay(
    compactBottom: Dp,
    compactStart: Dp,
    hidden: Boolean,
    expansionRequest: Long,
    focusRequester: FocusRequester,
    onCompactHeightChanged: (Dp) -> Unit,
    onVisibilityChanged: (Boolean) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    compactModifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var lastExpansion by rememberSaveable { mutableStateOf(0L) }
    var lastIntentExpansion by rememberSaveable { mutableStateOf(0L) }
    var extras by remember { mutableStateOf(Bundle.EMPTY) }
    val controller = rememberMediaController(
        ownsVideoSurface = false,
        onExtrasChanged = { extras = it },
    )
    LaunchedEffect(controller) { extras = controller?.sessionExtras ?: Bundle.EMPTY }
    if (controller == null) {
        LaunchedEffect(Unit) {
            onVisibilityChanged(false)
            onExpandedChanged(false)
            onCompactHeightChanged(0.dp)
        }
        return
    }
    val currentSnapshot = rememberPlaybackSnapshot(controller)
    var lastSnapshot by remember(controller) { mutableStateOf(currentSnapshot) }
    LaunchedEffect(currentSnapshot) {
        if (currentSnapshot.visible && currentSnapshot.item?.localConfiguration != null) {
            lastSnapshot = currentSnapshot
        }
    }
    val presentation = PlaybackPresentation.from(extras)
    val snapshot = currentSnapshot.retainedFor(
        presentation,
        lastSnapshot,
    )
    LaunchedEffect(expansionRequest) {
        if (expansionRequest > lastIntentExpansion) {
            expanded = true
            lastIntentExpansion = expansionRequest
        }
    }
    LaunchedEffect(presentation.expansionRequest) {
        if (presentation.expansionRequest > lastExpansion) {
            expanded = true
            lastExpansion = presentation.expansionRequest
        }
    }
    LaunchedEffect(snapshot.visible) { if (!snapshot.visible) expanded = false }
    var undoDeadline by rememberSaveable { mutableLongStateOf(0L) }
    var showUndo by rememberSaveable { mutableStateOf(false) }
    var contentHeight by remember { mutableStateOf(0.dp) }
    val undoVisible = showUndo && !snapshot.visible
    val undoHeight = compactPlaybackHeight(LocalDensity.current.fontScale)
    LaunchedEffect(undoDeadline) {
        if (undoDeadline > 0) {
            delay((undoDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            showUndo = false
        }
    }
    LaunchedEffect(snapshot.visible) { if (snapshot.visible) showUndo = false }
    LaunchedEffect(
        snapshot.visible,
        hidden,
        expanded,
        undoVisible,
    ) {
        onVisibilityChanged(!hidden && (undoVisible || (snapshot.visible && !expanded)))
        onExpandedChanged(snapshot.visible && !hidden && expanded)
    }
    LaunchedEffect(
        contentHeight,
        undoVisible,
        hidden,
        undoHeight,
    ) {
        onCompactHeightChanged(
            if (hidden) {
                0.dp
            } else if (undoVisible) {
                undoHeight + 8.dp
            } else {
                contentHeight
            },
        )
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun dismiss() {
        expanded = false
        scope.launch {
            val result = controller.playbackCommand(CustomCommands.DISMISS_PLAYBACK).await()
            if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                undoDeadline = result.extras.getLong(CustomCommands.DISMISSAL_DEADLINE_KEY)
                showUndo = undoDeadline > SystemClock.elapsedRealtime()
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        PlaybackOverlayContent(
            player = controller,
            snapshot = snapshot,
            presentation = presentation,
            expanded = expanded,
            onExpandedChanged = { expanded = it },
            compactBottom = compactBottom,
            compactStart = compactStart,
            hidden = hidden,
            focusRequester = focusRequester,
            onCompactHeightChanged = { contentHeight = it },
            onDismissPlayback = ::dismiss,
            onCommand = { controller.playbackCommand(it) },
            onStop = controller::stopPlayerSession,
            compactModifier = compactModifier,
            audioOutput = extras.getInt(
                CustomCommands.AUDIO_OUTPUT_KEY,
                CustomCommands.SYSTEM_AUDIO_OUTPUT,
            ),
            onAudioOutput = { controller.setAudioOutput(it) },
            onWatchVideo = {
                context.startActivity(
                    Intent(
                        context,
                        PlayerActivity::class.java,
                    )
                        .putExtra(
                            EXTRA_WATCH_VIDEO,
                            true,
                        ),
                )
            },
        )
        val direction = LocalLayoutDirection.current
        val cutout = WindowInsets.displayCutout.asPaddingValues()
        AnimatedVisibility(
            visible = undoVisible && !hidden,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(
                    start = compactStart + cutout.calculateStartPadding(direction) + 16.dp,
                    end = cutout.calculateEndPadding(direction) + 16.dp,
                    bottom = compactBottom + 8.dp,
                ),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            PlaybackDismissUndoBar(
                deadline = undoDeadline,
                onUndo = {
                    if (showUndo && !snapshot.visible) {
                        showUndo = false
                        controller.playbackCommand(CustomCommands.UNDO_DISMISSAL)
                    }
                },
                onClose = { showUndo = false },
                enabled = undoVisible && !hidden,
                modifier = Modifier.heightIn(min = undoHeight).focusRequester(focusRequester)
                    .then(compactModifier).focusGroup(),
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackOverlayContent(
    player: Player,
    snapshot: PlaybackSnapshot,
    presentation: PlaybackPresentation,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    compactBottom: Dp,
    compactStart: Dp,
    hidden: Boolean,
    focusRequester: FocusRequester,
    onCompactHeightChanged: (Dp) -> Unit,
    onDismissPlayback: () -> Unit,
    onCommand: (CustomCommands) -> Unit,
    onStop: () -> Unit,
    onWatchVideo: () -> Unit,
    compactModifier: Modifier = Modifier,
    audioOutput: Int = CustomCommands.SYSTEM_AUDIO_OUTPUT,
    onAudioOutput: (Int) -> Unit = {},
) {
    val density = LocalDensity.current
    val compactHeight = compactPlaybackHeight(density.fontScale)
    LaunchedEffect(
        compactHeight,
        hidden,
        snapshot.visible,
    ) {
        onCompactHeightChanged(if (snapshot.visible && !hidden) compactHeight + 8.dp else 0.dp)
    }
    var output by rememberSaveable { mutableStateOf(false) }
    val queueScope = rememberCoroutineScope()
    val queueSheet = remember { PlaybackQueueSheet(queueScope) }
    queueSheet.settleSpec =
        MaterialTheme.motionScheme.defaultSpatialSpec<Float>() as SpringSpec<Float>
    queueSheet.flingThreshold = with(density) { 400.dp.toPx() }
    BackHandler(expanded && !hidden && snapshot.visible) {
        when {
            queueSheet.engaged -> queueSheet.stepBack()
            output -> output = false
            else -> onExpandedChanged(false)
        }
    }
    if (!snapshot.visible || hidden) return
    val controlsEnabled = presentation.connection != PlaybackConnection.CONNECTING &&
        presentation.connection != PlaybackConnection.LOST
    val title = playbackTitle(snapshot)
    val secondary = playbackSecondaryText(
        snapshot,
        presentation.connection,
        presentation.deviceName,
    )
    val artworkPainter = rememberPlaybackArtworkPainter(snapshot)
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var compactArtwork by remember { mutableStateOf(Rect.Zero) }
    var expandedArtwork by remember { mutableStateOf(Rect.Zero) }
    var expandedHeader by remember { mutableStateOf(Rect.Zero) }
    var compactMetadata by remember { mutableStateOf(Rect.Zero) }
    var expandedMetadata by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned {
            rootOrigin = it.positionInRoot()
        },
    ) {
        val landscape = maxWidth > maxHeight
        val travel = with(density) {
            (maxHeight - compactBottom - compactHeight - 8.dp).toPx()
        }.coerceAtLeast(1f)
        var dragging by remember { mutableStateOf(false) }
        val expansion = remember {
            Animatable(
                if (expanded) 1f else 0f,
                visibilityThreshold = EXPANSION_THRESHOLD,
            )
        }
        val themeSpring =
            MaterialTheme.motionScheme.defaultSpatialSpec<Float>() as SpringSpec<Float>
        val expansionSpring = remember(themeSpring) {
            spring(
                themeSpring.dampingRatio,
                themeSpring.stiffness,
                EXPANSION_THRESHOLD,
            )
        }
        val motion = remember { ExpansionMotion(expansion) }
        val gestureScope = rememberCoroutineScope()
        LaunchedEffect(expanded) {
            motion.settle(
                expanded,
                expansionSpring,
            )
        }
        val isCollapsed by remember { derivedStateOf { expansion.value == 0f } }
        val isFullyOpen by remember { derivedStateOf { expansion.value == 1f } }
        val interactive by remember { derivedStateOf { expansion.value >= .99f } }
        val currentlyExpanded by rememberUpdatedState(expanded)
        // Pulling the open player up raises its queue; the gesture keeps that target.
        var queueGesture by remember { mutableStateOf<Boolean?>(null) }
        // Collapsing flies the artwork straight from where the queue left it; the queue resets
        // only once the player has closed.
        LaunchedEffect(isCollapsed) { if (isCollapsed) queueSheet.snapTo(QueueStage.PLAYER) }
        // Landscape opens the queue in one stage; turning the phone starts from the player.
        queueSheet.halfStage = !landscape
        LaunchedEffect(landscape) { queueSheet.snapTo(QueueStage.PLAYER) }
        val dragState = rememberDraggableState { delta ->
            val toQueue = queueGesture ?: (
                currentlyExpanded && expansion.value >= .99f &&
                    (queueSheet.engaged || delta < 0f)
                ).also { queueGesture = it }
            if (toQueue) {
                // A swipe can catch the expand spring just short of open; finish it, or the
                // player stays mid-transition and its morphing artwork covers the queue banner.
                if (expansion.value < 1f) {
                    gestureScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        expansion.snapTo(1f)
                    }
                }
                queueSheet.dragBy(delta)
                return@rememberDraggableState
            }
            gestureScope.launch(start = CoroutineStart.UNDISPATCHED) {
                expansion.snapTo(
                    (expansion.value - delta / travel).coerceIn(
                        0f,
                        1f,
                    ),
                )
            }
        }
        val dragStarted: suspend kotlinx.coroutines.CoroutineScope.(
            androidx.compose.ui.geometry.Offset,
        ) -> Unit = {
            expansion.stop()
            queueGesture = null
            dragging = true
        }
        val dragStopped: suspend kotlinx.coroutines.CoroutineScope.(
            Float,
        ) -> Unit = stop@{ velocity ->
            if (queueGesture == true) {
                queueGesture = null
                dragging = false
                queueSheet.settle(velocity)
                return@stop
            }
            queueGesture = null
            val threshold = minOf(
                travel * .25f,
                with(density) { 32.dp.toPx() },
            )
            val target = when {
                abs(velocity) > 500f -> velocity < 0
                expanded -> (1f - expansion.value) * travel < threshold
                else -> expansion.value * travel > threshold
            }
            dragging = false
            motion.carryVelocity(-velocity / travel)
            if (target == expanded) {
                motion.settle(
                    target,
                    expansionSpring,
                )
            } else {
                onExpandedChanged(target)
            }
        }
        val verticalDrag = Modifier.draggable(
            dragState,
            Orientation.Vertical,
            enabled = expanded,
            onDragStarted = dragStarted,
            onDragStopped = dragStopped,
        )
        val direction = LocalLayoutDirection.current
        val cutout = WindowInsets.displayCutout.asPaddingValues()
        val rtl = direction == androidx.compose.ui.unit.LayoutDirection.Rtl
        val compactStartInset = compactStart + cutout.calculateStartPadding(direction) + 16.dp
        val compactEndInset = cutout.calculateEndPadding(direction) + 16.dp
        val leftInset = if (rtl) compactEndInset else compactStartInset
        val rightInset = if (rtl) compactStartInset else compactEndInset
        val fullWidth = maxWidth
        val fullHeight = maxHeight
        val sheetBounds: () -> Rect = {
            val progress = expansion.value
            val collapse = (1f - progress).coerceIn(
                0f,
                1f,
            )
            val top = travel * (1f - progress)
            val growth = progress.coerceAtLeast(0f)
            with(density) {
                Rect(
                    (leftInset * collapse).toPx(),
                    top,
                    (fullWidth - rightInset * collapse).toPx(),
                    top + (compactHeight + (fullHeight - compactHeight) * growth).toPx(),
                )
            }
        }
        val transitioning = !isCollapsed && !isFullyOpen
        // The expanded rects move every frame while the queue moves; only their presence recomposes.
        val artworkMeasured by remember {
            derivedStateOf { compactArtwork.width > 0 && expandedArtwork.width > 0 }
        }
        val metadataMeasured by remember {
            derivedStateOf { compactMetadata.width > 0 && expandedMetadata.width > 0 }
        }
        val morphArtwork = transitioning && artworkMeasured
        val morphMetadata = transitioning && metadataMeasured
        val artworkBounds: () -> Rect = {
            morphBounds(
                compactArtwork,
                expandedArtwork,
                expansion.value,
                travel,
            )
        }
        val metadataBounds: () -> Rect = {
            routedMetadataBounds(
                compactArtwork,
                expandedArtwork,
                compactMetadata,
                expandedMetadata,
                artworkBounds(),
                expansion.value,
                travel,
            )
        }
        // The header rides on the sheet's top edge and fades in over the last stretch of the way.
        val headerShift: () -> Float = {
            val collapse = 1f - expansion.value.coerceIn(
                0f,
                1f,
            )
            sheetBounds().top - expandedHeader.top * collapse
        }
        val scrim = MaterialTheme.colorScheme.scrim
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                if (expansion.value > 0f) {
                    drawRect(
                        scrim,
                        alpha = expansion.value.coerceAtMost(1f) * EXPANSION_SCRIM_ALPHA,
                    )
                }
            },
        )
        if (!isCollapsed || expanded || dragging) {
            val sheetColor = MaterialTheme.colorScheme.primaryContainer
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val bounds = sheetBounds()
                        val placeable = measurable.measure(
                            Constraints.fixed(
                                bounds.width.roundToInt().coerceAtLeast(0),
                                bounds.height.roundToInt().coerceAtLeast(0),
                            ),
                        )
                        layout(
                            constraints.maxWidth,
                            constraints.maxHeight,
                        ) {
                            placeable.place(
                                bounds.left.roundToInt(),
                                bounds.top.roundToInt(),
                            )
                        }
                    }
                    .graphicsLayer {
                        shape = RoundedCornerShape(
                            24.dp * (1f - expansion.value).coerceIn(
                                0f,
                                1f,
                            ),
                        )
                        clip = true
                        shadowElevation = 3.dp.toPx()
                    }
                    .background(sheetColor)
                    .testTag("playback_sheet")
                    .draggable(
                        dragState,
                        Orientation.Vertical,
                        enabled = !expanded,
                        onDragStarted = dragStarted,
                        onDragStopped = dragStopped,
                    )
                    .pointerInput(Unit) { detectTapGestures {} },
            )
        }
        // The mini player owns an expanding drag; removing it mid-gesture would drop the release.
        if (!isFullyOpen || dragging) {
            val compactIdentity = player.playbackQueueIdentity()
            key(
                player,
                compactIdentity,
            ) {
                PlaybackSwipeToDismiss(
                    onDismiss = {
                        if (player.playbackQueueIdentity() == compactIdentity) onDismissPlayback()
                    },
                    enabled = isCollapsed,
                    bothDirections = true,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                0,
                                (travel * (1f - expansion.value.coerceAtMost(0f))).roundToInt(),
                            )
                        }
                        .padding(
                            start = compactStart + cutout.calculateStartPadding(direction),
                            end = cutout.calculateEndPadding(direction),
                        )
                        .padding(horizontal = 16.dp)
                        .testTag("compact_swipe"),
                ) {
                    MiniPlayer(
                        player = player,
                        onOpenPlayer = { if (isCollapsed) onExpandedChanged(true) },
                        onStop = onDismissPlayback,
                        deviceName = presentation.deviceName,
                        controlsEnabled = controlsEnabled,
                        snapshot = snapshot,
                        artworkPainter = artworkPainter,
                        showContainer = isCollapsed,
                        artworkModifier = Modifier.onGloballyPositioned {
                            if (expansion.value >= 0f) {
                                compactArtwork = it.boundsInRoot().translate(-rootOrigin)
                            }
                        }.graphicsLayer { alpha = if (morphArtwork) 0f else 1f },
                        metadataModifier = Modifier.onGloballyPositioned {
                            if (expansion.value >= 0f) {
                                compactMetadata = it.boundsInRoot().translate(-rootOrigin)
                            }
                        }.graphicsLayer { alpha = if (morphMetadata) 0f else 1f },
                        secondaryText = secondary,
                        connecting = presentation.connection == PlaybackConnection.CONNECTING,
                        modifier = Modifier
                            .draggable(
                                dragState,
                                Orientation.Vertical,
                                enabled = !expanded,
                                onDragStarted = dragStarted,
                                onDragStopped = dragStopped,
                            )
                            .focusRequester(focusRequester).then(compactModifier)
                            .semantics { if (!isCollapsed) hideFromAccessibility() }
                            .focusProperties { canFocus = isCollapsed }
                            .graphicsLayer {
                                alpha = (1f - expansion.value * 3f).coerceIn(
                                    0f,
                                    1f,
                                )
                            },
                    )
                }
            }
        }
        if (!isCollapsed || expanded || dragging) {
            ExpandedPlaybackControls(
                player,
                snapshot,
                title,
                secondary,
                controlsEnabled,
                landscape = landscape,
                connection = presentation.connection,
                deviceName = presentation.deviceName,
                onCollapse = { onExpandedChanged(false) },
                onQueue = {
                    queueSheet.animateTo(QueueStage.QUEUE)
                },
                onOutput = { output = true },
                onStop = onStop,
                onWatchVideo = onWatchVideo,
                onCommand = onCommand,
                dragModifier = verticalDrag,
                queueSheet = queueSheet,
                interactive = interactive,
                transitionProgress = { expansion.value },
                controlsTranslationY = {
                    if (morphMetadata) {
                        (compactMetadata.bottom - expandedMetadata.bottom) * (1f - expansion.value)
                    } else {
                        0f
                    }
                },
                artworkModifier = Modifier.onGloballyPositioned {
                    expandedArtwork = it.boundsInRoot().translate(-rootOrigin)
                }.graphicsLayer { alpha = if (morphArtwork) 0f else 1f },
                headerModifier = Modifier.onGloballyPositioned {
                    expandedHeader = it.boundsInRoot().translate(-rootOrigin)
                }.graphicsLayer {
                    if (morphArtwork && expandedHeader.height > 0f) {
                        translationY = headerShift()
                        alpha =
                            ((expansion.value - HEADER_REVEAL_START) / (1f - HEADER_REVEAL_START))
                                .coerceIn(
                                    0f,
                                    1f,
                                )
                    }
                },
                metadataModifier = Modifier.onGloballyPositioned {
                    expandedMetadata = it.boundsInRoot().translate(-rootOrigin)
                }.graphicsLayer { alpha = if (morphMetadata) 0f else 1f },
                modifier = Modifier.semantics {
                    if (!interactive) hideFromAccessibility()
                }.focusProperties {
                    if (!interactive) onEnter = { cancelFocusChange() }
                }.drawWithContent {
                    // Closed under a held finger, the sheet is the mini player; landscape's
                    // centred artwork would otherwise show through it.
                    if (expansion.value == 0f) return@drawWithContent
                    val clip = sheetBounds()
                    clipRect(
                        left = clip.left,
                        top = clip.top,
                        right = clip.right,
                        bottom = clip.bottom,
                    ) {
                        this@drawWithContent.drawContent()
                    }
                },
            )
        }
        if (morphArtwork) {
            PlaybackArtwork(
                snapshot,
                Modifier.align(AbsoluteAlignment.TopLeft).followBounds(artworkBounds)
                    .graphicsLayer {
                        // Starts from the queue banner's corners when the queue was open.
                        val open = if (queueSheet.engaged) {
                            queueArtworkCorner(queueSheet.progress)
                        } else {
                            18.dp
                        }
                        shape = RoundedCornerShape(
                            lerp(
                                8.dp,
                                open,
                                expansion.value.coerceIn(
                                    0f,
                                    1f,
                                ),
                            ),
                        )
                        clip = true
                    }
                    .clearAndSetSemantics { testTag = "transition_artwork" },
                RectangleShape,
                artworkPainter,
            )
        }
        if (morphMetadata) {
            PlaybackMorphingMetadata(
                title = title,
                secondary = secondary,
                progress = { expansion.value },
                modifier = Modifier.align(AbsoluteAlignment.TopLeft).followBounds(metadataBounds)
                    .clearAndSetSemantics { testTag = "transition_metadata" },
            )
        }
    }

    LaunchedEffect(controlsEnabled) {
        if (!controlsEnabled) {
            output = false
        }
    }
    if (output && controlsEnabled) {
        PlaybackOutputSheet(
            player,
            snapshot,
            presentation,
            audioOutput,
            onAudioOutput,
            onDismiss = { output = false },
        )
    }
}

private const val EXPANSION_THRESHOLD = .0005f
private const val HEADER_REVEAL_START = .8f
private const val EXPANSION_SCRIM_ALPHA = .32f

/** One Material expressive spring for tap and drag; it keeps the velocity of a release. */
private class ExpansionMotion(private val expansion: Animatable<Float, AnimationVector1D>) {
    private var lastVelocity = 0f

    fun carryVelocity(value: Float) {
        lastVelocity = value
    }

    suspend fun settle(open: Boolean, animationSpec: SpringSpec<Float>) {
        expansion.animateTo(
            if (open) 1f else 0f,
            animationSpec,
            initialVelocity = lastVelocity,
        ) { lastVelocity = velocity }
        lastVelocity = 0f
    }
}

private fun Modifier.followBounds(bounds: () -> Rect): Modifier = this
    .absoluteOffset {
        IntOffset(
            bounds().left.roundToInt(),
            bounds().top.roundToInt(),
        )
    }
    .layout { measurable, _ ->
        val placeable = measurable.measure(
            Constraints.fixed(
                bounds().width.roundToInt().coerceAtLeast(0),
                bounds().height.roundToInt().coerceAtLeast(0),
            ),
        )
        layout(
            placeable.width,
            placeable.height,
        ) {
            placeable.place(
                0,
                0,
            )
        }
    }

/** Reads progress itself so only this text recomposes while the player transitions. */
@Composable
private fun PlaybackMorphingMetadata(
    title: String,
    secondary: String,
    progress: () -> Float,
    modifier: Modifier,
) {
    val value = progress().coerceIn(
        0f,
        1f,
    )
    Column(modifier) {
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.lerp(
                MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-.2).sp,
                ),
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                value,
            ).copy(textMotion = TextMotion.Animated),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.lerp(
                MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                MaterialTheme.typography.titleMedium,
                value,
            ).copy(textMotion = TextMotion.Animated),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

internal fun smoothStep(start: Float, end: Float, value: Float): Float {
    val position = ((value - start) / (end - start)).coerceIn(
        0f,
        1f,
    )
    return position * position * (3f - 2f * position)
}

/** Past the collapsed end of the spring, the compact player only moves down. */
private fun morphBounds(compact: Rect, expanded: Rect, progress: Float, travel: Float): Rect =
    if (progress < 0f) {
        compact.translate(
            0f,
            -progress * travel,
        )
    } else {
        lerp(
            compact,
            expanded,
            progress,
        )
    }

/** Carries the title beside the growing artwork and then below it, never through it. */
internal fun routedMetadataBounds(
    compactArtwork: Rect,
    expandedArtwork: Rect,
    compactMetadata: Rect,
    expandedMetadata: Rect,
    artwork: Rect,
    progress: Float,
    travel: Float,
): Rect {
    if (progress < 0f || expandedMetadata.top < expandedArtwork.bottom) {
        return morphBounds(
            compactMetadata,
            expandedMetadata,
            progress,
            travel,
        )
    }
    val size = progress.coerceAtMost(1f)
    val width = androidx.compose.ui.util.lerp(
        compactMetadata.width,
        expandedMetadata.width,
        size,
    )
    val height = androidx.compose.ui.util.lerp(
        compactMetadata.height,
        expandedMetadata.height,
        size,
    )
    val besideArtwork = if (compactMetadata.left >= compactArtwork.right) {
        artwork.right + compactMetadata.left - compactArtwork.right
    } else {
        artwork.left - (compactArtwork.left - compactMetadata.right) - width
    }
    val left = androidx.compose.ui.util.lerp(
        besideArtwork,
        expandedMetadata.left,
        smoothStep(
            .3f,
            .8f,
            progress,
        ),
    )
    val top = androidx.compose.ui.util.lerp(
        compactMetadata.top,
        artwork.bottom + expandedMetadata.top - expandedArtwork.bottom,
        smoothStep(
            0f,
            .3f,
            progress,
        ),
    )
    // Beside the artwork the title narrows instead of running past the sheet's edge.
    return Rect(
        maxOf(
            left,
            minOf(
                compactMetadata.left,
                expandedMetadata.left,
            ),
        ),
        top,
        minOf(
            left + width,
            maxOf(
                compactMetadata.right,
                expandedMetadata.right,
            ),
        ),
        top + height,
    )
}

private fun Player.playbackQueueIdentity(): Any? = when {
    isCommandAvailable(Player.COMMAND_GET_TIMELINE) && !currentTimeline.isEmpty ->
        currentTimeline.getWindow(
            0,
            Timeline.Window(),
        ).uid

    isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) -> currentMediaItem?.mediaId

    else -> null
}

private fun compactPlaybackHeight(fontScale: Float): Dp = maxOf(
    64.dp,
    (38 * fontScale + 20).dp,
)
