package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaylistState
import dev.anilbeesetti.nextplayer.feature.player.ui.PlaybackQueueContent
import kotlin.math.roundToInt

/** Where the artwork and the queue panel rest at each stage, in pixels within the player. */
internal class QueueStageGeometry(
    width: Float,
    height: Float,
    topInset: Float,
    blockHeight: Float,
    density: Density,
) {
    val gap = with(density) { 8.dp.toPx() }
    val rowHeightDp = 72.dp
    private val rowHeight = with(density) { rowHeightDp.toPx() }

    /** In the queue the artwork becomes the mini row's thumbnail. */
    val thumbnail = with(density) {
        Rect(
            16.dp.toPx(),
            12.dp.toPx(),
            64.dp.toPx(),
            60.dp.toPx(),
        )
    }
    val queuePanelTop = rowHeight + with(density) { 4.dp.toPx() }

    /** Half way the artwork is a full-width square, up behind the status bar. */
    val halfArtwork: Rect
    val halfPanelTop: Float

    /** On shorter screens the half stage tightens the controls to give the queue more room. */
    val seekLift: Float
    val transportLift: Float
    val transportCut: Float

    init {
        // Phones get the full width; only very short windows shrink the square to keep the queue.
        // The title rests two gaps above the artwork's bottom; the block runs on to the transport.
        val room = height - with(density) { 160.dp.toPx() } - blockHeight + gap + topInset
        val side = room.coerceIn(
            minOf(
                rowHeight * 2,
                width,
            ),
            width,
        )
        val left = (width - side) / 2
        halfArtwork = Rect(
            left,
            -topInset,
            left + side,
            side - topInset,
        )
        val loose = halfArtwork.bottom - gap * 2 + blockHeight + gap
        val most = with(density) {
            floatArrayOf(
                12.dp.toPx(),
                12.dp.toPx(),
                16.dp.toPx(),
            )
        }
        val squeeze = ((loose - height * .6f) / most.sum()).coerceIn(
            0f,
            1f,
        )
        seekLift = most[0] * squeeze
        transportLift = seekLift + most[1] * squeeze
        transportCut = most[2] * squeeze
        halfPanelTop = loose - transportLift - transportCut
    }
}

/** The artwork while the queue shows: it grows into a banner and settles as a thumbnail. */
@Composable
internal fun QueueArtworkBanner(
    snapshot: PlaybackSnapshot,
    bounds: () -> Rect,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val fade = MaterialTheme.colorScheme.primaryContainer
    val scrim = MaterialTheme.colorScheme.scrim
    Box(
        Modifier.followBounds(bounds).graphicsLayer {
            val value = progress()
            val corner = if (value <= 1f) {
                lerp(
                    18.dp.toPx(),
                    0f,
                    value.coerceAtLeast(0f),
                )
            } else {
                lerp(
                    0f,
                    12.dp.toPx(),
                    (value - 1f).coerceAtMost(1f),
                )
            }
            shape = RoundedCornerShape(corner.coerceAtLeast(0f))
            clip = true
        }.then(modifier).testTag("queue_artwork"),
    ) {
        PlaybackArtwork(
            snapshot,
            Modifier.fillMaxSize(),
            RectangleShape,
        )
        // The banner melts into the player at the bottom and keeps the header readable on top.
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val value = progress()
                val strength = smoothStep(
                    .05f,
                    .6f,
                    value,
                ) * (
                    1f - smoothStep(
                        1f,
                        1.4f,
                        value,
                    )
                    )
                if (strength <= 0f) return@drawBehind
                drawRect(
                    Brush.verticalGradient(
                        0f to scrim.copy(alpha = .4f),
                        .3f to Color.Transparent,
                    ),
                    alpha = strength,
                )
                drawRect(
                    Brush.verticalGradient(
                        .35f to Color.Transparent,
                        1f to fade,
                    ),
                    alpha = strength,
                )
            },
        )
    }
}

/** The player folded into one row above the queue; the banner supplies its thumbnail. */
@Composable
internal fun QueueMiniRow(
    player: Player,
    snapshot: PlaybackSnapshot,
    title: String,
    secondary: String,
    controlsEnabled: Boolean,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().graphicsLayer {
            val value = progress()
            alpha = smoothStep(
                1.4f,
                2f,
                value,
            )
            translationY = 12.dp.toPx() * (
                1f - smoothStep(
                    1.3f,
                    2f,
                    value,
                )
                )
        }.clickable(onClick = onClick).padding(end = 8.dp).testTag("queue_mini_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(76.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        PlaybackCompactTransport(
            player,
            snapshot,
            controlsEnabled,
        )
    }
}

/** The queue under the player; its handle drags it and the list hands it scrolls first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueuePanel(
    player: Player,
    sheet: PlaybackQueueSheet,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val bottomInsetPx = with(LocalDensity.current) { bottomInset.roundToPx() }
    val queue = rememberPlaylistState(player)
    val drag = rememberDraggableState { sheet.dragBy(it) }
    val connection = remember(sheet) { QueueScrollConnection(sheet) }
    Surface(
        // The panel always reaches the bottom, so its last rows and Undo stay on screen.
        modifier.layout { measurable, constraints ->
            val top = sheet.panelTop.roundToInt().coerceIn(
                0,
                constraints.maxHeight,
            )
            val placeable = measurable.measure(
                Constraints.fixed(
                    constraints.maxWidth,
                    constraints.maxHeight - top + bottomInsetPx,
                ),
            )
            layout(
                constraints.maxWidth,
                constraints.maxHeight,
            ) {
                placeable.place(
                    0,
                    top,
                )
            }
        }.testTag("playback_queue"),
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 6.dp,
    ) {
        Column {
            Column(
                Modifier.fillMaxWidth().draggable(
                    drag,
                    Orientation.Vertical,
                    onDragStopped = { sheet.settle(it) },
                ).padding(bottom = 8.dp),
            ) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.queue_next_up),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.queue_videos_count,
                            queue.mediaItemCount,
                            queue.mediaItemCount,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PlaybackQueueContent(
                player,
                Modifier.weight(1f).nestedScroll(connection).padding(bottom = bottomInset),
                showHeader = false,
            )
        }
    }
}

/** The list scrolls in place; pulling down past its top lowers the panel, and scrolling back undoes that. */
private class QueueScrollConnection(private val sheet: PlaybackQueueSheet) :
    NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f && source == NestedScrollSource.UserInput) {
            Offset(
                0f,
                sheet.raiseBy(available.y),
            )
        } else {
            Offset.Zero
        }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (available.y > 0f && source == NestedScrollSource.UserInput) {
        Offset(
            0f,
            sheet.dragBy(available.y),
        )
    } else {
        Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (sheet.atRest) return Velocity.Zero
        sheet.settle(available.y)
        return available
    }
}

private fun Modifier.followBounds(bounds: () -> Rect): Modifier = this
    .offset {
        IntOffset(
            bounds().left.roundToInt(),
            bounds().top.roundToInt(),
        )
    }
    .layout { measurable, _ ->
        val rect = bounds()
        val placeable = measurable.measure(
            Constraints.fixed(
                rect.width.roundToInt().coerceAtLeast(0),
                rect.height.roundToInt().coerceAtLeast(0),
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
