package dev.anilbeesetti.nextplayer.feature.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.cast.CastButton
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.EXTRA_WATCH_VIDEO
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.service.playbackCommand
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaController
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.ui.PlaylistView
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
    LaunchedEffect(
        snapshot.visible,
        hidden,
        expanded,
    ) {
        onVisibilityChanged(snapshot.visible && !hidden && !expanded)
        onExpandedChanged(snapshot.visible && !hidden && expanded)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val stopped = stringResource(R.string.playback_dismissed)
    val undo = stringResource(R.string.undo)
    fun dismiss() {
        expanded = false
        scope.launch {
            val result = controller.playbackCommand(CustomCommands.DISMISS_PLAYBACK).await()
            if (result.resultCode !=
                androidx.media3.session.SessionResult.RESULT_SUCCESS
            ) {
                return@launch
            }
            val expiry = launch {
                delay(4_000)
                snackbar.currentSnackbarData?.dismiss()
            }
            try {
                if (snackbar.showSnackbar(
                        stopped,
                        undo,
                        duration = SnackbarDuration.Indefinite,
                    ) ==
                    SnackbarResult.ActionPerformed
                ) {
                    controller.playbackCommand(CustomCommands.UNDO_DISMISSAL)
                }
            } finally {
                expiry.cancel()
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
            onCompactHeightChanged = onCompactHeightChanged,
            onDismissPlayback = ::dismiss,
            onCommand = { controller.playbackCommand(it) },
            onStop = controller::stopPlayerSession,
            compactModifier = compactModifier,
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
        if (!hidden) {
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = compactBottom).padding(16.dp),
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
) {
    val density = LocalDensity.current
    val compactHeight = maxOf(
        64.dp,
        (38 * density.fontScale + 20).dp,
    )
    LaunchedEffect(
        compactHeight,
        hidden,
        snapshot.visible,
    ) {
        onCompactHeightChanged(if (snapshot.visible && !hidden) compactHeight + 8.dp else 0.dp)
    }
    var queue by rememberSaveable { mutableStateOf(false) }
    var output by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded && !hidden && snapshot.visible) {
        when {
            queue -> queue = false
            output -> output = false
            else -> onExpandedChanged(false)
        }
    }
    if (!snapshot.visible || hidden) return
    val controlsEnabled = presentation.connection != PlaybackConnection.CONNECTING &&
        presentation.connection != PlaybackConnection.LOST
    val title = snapshot.metadata.title?.toString()?.takeIf { it.isNotBlank() }
        ?: snapshot.item?.localConfiguration?.uri?.lastPathSegment
        ?: stringResource(R.string.player_name)
    val secondary = when (presentation.connection) {
        PlaybackConnection.CONNECTING -> stringResource(
            R.string.connecting_to_device,
            presentation.deviceName.orEmpty(),
        )

        PlaybackConnection.LOST -> stringResource(
            R.string.cast_connection_lost,
            presentation.deviceName.orEmpty(),
        )

        PlaybackConnection.CAST -> stringResource(
            R.string.casting_on_device,
            presentation.deviceName.orEmpty(),
        )

        PlaybackConnection.STOPPED, PlaybackConnection.LOCAL ->
            snapshot.metadata.subtitle?.toString()
                ?: snapshot.item?.localConfiguration?.uri?.path?.substringBeforeLast(
                    '/',
                )?.substringAfterLast('/')
                ?: stringResource(R.string.background_play)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val travel = with(density) {
            (maxHeight - compactBottom - compactHeight - 8.dp).toPx()
        }.coerceAtLeast(1f)
        var dragging by remember { mutableStateOf(false) }
        var upwardPull by remember { mutableFloatStateOf(0f) }
        val expansion = remember { Animatable(if (expanded) 1f else 0f) }
        val gestureScope = rememberCoroutineScope()
        LaunchedEffect(expanded) { expansion.animateTo(if (expanded) 1f else 0f) }
        val progress = expansion.value
        val dragState = rememberDraggableState { delta ->
            if (controlsEnabled && expanded && progress >= .99f && delta < 0f) {
                upwardPull -= delta
                if (upwardPull > with(density) { 32.dp.toPx() }) queue = true
            } else if (delta > 0f) {
                upwardPull = 0f
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
            upwardPull = 0f
            dragging = true
        }
        val dragStopped: suspend kotlinx.coroutines.CoroutineScope.(Float) -> Unit = { velocity ->
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
            if (target == expanded) {
                expansion.animateTo(if (target) 1f else 0f)
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
        val start = (compactStart + cutout.calculateStartPadding(direction)) * (1f - progress)
        val end = cutout.calculateEndPadding(direction) * (1f - progress)
        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        0,
                        (travel * (1f - progress)).roundToInt(),
                    )
                }
                .padding(
                    start = start,
                    end = end,
                )
                .padding(horizontal = 16.dp * (1f - progress))
                .fillMaxWidth()
                .height(compactHeight + (maxHeight - compactHeight) * progress)
                .testTag("playback_sheet")
                .draggable(
                    dragState,
                    Orientation.Vertical,
                    enabled = !expanded,
                    onDragStarted = dragStarted,
                    onDragStopped = dragStopped,
                )
                .pointerInput(Unit) { detectTapGestures {} },
            shape = RoundedCornerShape(24.dp * (1f - progress)),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 3.dp,
        ) {
            if (progress < .01f && !expanded && !dragging) {
                var horizontal by remember { mutableFloatStateOf(0f) }
                MiniPlayer(
                    player = player,
                    onOpenPlayer = { onExpandedChanged(true) },
                    onStop = onDismissPlayback,
                    deviceName = presentation.deviceName,
                    controlsEnabled = controlsEnabled,
                    snapshot = snapshot,
                    secondaryText = secondary,
                    connecting = presentation.connection == PlaybackConnection.CONNECTING,
                    modifier = Modifier.focusRequester(focusRequester).then(compactModifier)
                        .graphicsLayer { translationX = horizontal }
                        .pointerInput(player) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    horizontal +=
                                        amount
                                },
                                onDragCancel = { horizontal = 0f },
                                onDragEnd = {
                                    if (abs(horizontal) > size.width * .25f) onDismissPlayback()
                                    horizontal = 0f
                                },
                            )
                        },
                )
            } else {
                ExpandedPlaybackControls(
                    player,
                    snapshot,
                    title,
                    secondary,
                    controlsEnabled,
                    landscape = maxWidth > maxHeight,
                    connection = presentation.connection,
                    onCollapse = { onExpandedChanged(false) },
                    onQueue = { queue = true },
                    onOutput = { output = true },
                    onStop = onStop,
                    onWatchVideo = onWatchVideo,
                    onCommand = onCommand,
                    dragModifier = verticalDrag,
                )
            }
        }
    }
    LaunchedEffect(controlsEnabled) {
        if (!controlsEnabled) {
            queue = false
            output = false
        }
    }
    if (queue && controlsEnabled) {
        ModalBottomSheet(
            onDismissRequest = { queue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Box(Modifier.fillMaxWidth().height(500.dp).testTag("playback_queue")) {
                PlaylistView(
                    show = true,
                    player = player,
                    fullSize = true,
                )
            }
        }
    }
    if (output && controlsEnabled) {
        ModalBottomSheet(
            onDismissRequest = { output = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.titleMedium,
                )
                CastButton(MaterialTheme.colorScheme.onSurface)
                PlaybackVolumeControl(
                    player,
                    snapshot,
                )
            }
        }
    }
}
