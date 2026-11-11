package dev.anilbeesetti.nextplayer.feature.player.ui

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.player.PlaybackArtwork
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaylistState
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.PlaylistView(
    modifier: Modifier = Modifier,
    show: Boolean,
    player: Player,
    fullSize: Boolean = false,
) {
    OverlayView(
        modifier,
        show,
        stringResource(R.string.now_playing),
        fullSize,
    ) {
        PlaybackQueueContent(
            player,
            showHeader = false,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun PlaybackQueueContent(
    player: Player,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val queue = rememberPlaylistState(player)
    val list = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val dragStartFeedback = HapticFeedbackType.GestureThresholdActivate
    val artworkSize = maxOf(
        60.dp,
        (48 * LocalDensity.current.fontScale).dp,
    )
    val reorder = rememberReorderableLazyListState(list) { from, to ->
        queue.moveItemAndAwait(
            from.key as String,
            to.key as String,
        )
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(
        queue.mediaItemCount,
        queue.currentMediaItemIndex,
    ) {
        if (!positioned && queue.currentMediaItemIndex in 0 until queue.mediaItemCount) {
            list.scrollToItem(queue.currentMediaItemIndex)
            positioned = true
        }
    }
    val editable = player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)
    val selectable = player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
    val moveUp = stringResource(R.string.queue_move_up)
    val moveDown = stringResource(R.string.queue_move_down)
    val snapshot = PlaybackSnapshot.from(player)
    val removal = queue.removalUndo
    LaunchedEffect(removal?.deadline) {
        removal?.let {
            delay((it.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            queue.clearRemovalUndo()
        }
    }
    val removeLabel = stringResource(R.string.remove)
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (showHeader) {
                Column(
                    Modifier.padding(
                        horizontal = 24.dp,
                        vertical = 12.dp,
                    ),
                ) {
                    Text(
                        stringResource(R.string.queue_next_up),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.queue_videos_count,
                            queue.mediaItemCount,
                            queue.mediaItemCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (queue.mediaItemCount == 0) {
                Text(
                    stringResource(R.string.no_videos_in_queue),
                    Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("playback_queue_list"),
                    state = list,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = if (removal != null) (if (showHeader) 164.dp else 84.dp) else 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        queue.mediaItemCount,
                        key = queue::getKeyAt,
                    ) { index ->
                        val key = queue.getKeyAt(index)
                        val item = queue.getMediaItemAt(index)
                        val current = index == queue.currentMediaItemIndex
                        val canMoveEarlier = editable && index > 0
                        val canMoveLater = editable && index < queue.mediaItemCount - 1
                        val shape = RoundedCornerShape(if (current) 60.dp else 22.dp)
                        val removable = queue.canRemoveItem(index)
                        val remove = { queue.removeItem(queue.indexOfKey(key)) }
                        var rowFocused by remember(key) { mutableStateOf(false) }
                        ReorderableItem(
                            reorder,
                            key = key,
                        ) { isDragging ->
                            PlaybackSwipeToDismiss(
                                onDismiss = remove,
                                enabled = removable && !reorder.isAnyItemDragging,
                            ) {
                                Surface(
                                    onClick = { queue.seekToItem(queue.indexOfKey(key)) },
                                    enabled = selectable,
                                    shape = shape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    tonalElevation = if (isDragging) 4.dp else 1.dp,
                                    modifier = Modifier.fillMaxWidth().testTag(
                                        "queue_item_$index",
                                    ).onFocusChanged { rowFocused = it.hasFocus }.tvFocusRing(shape)
                                        .semantics {
                                            selected = current
                                            if (editable) {
                                                customActions = buildList {
                                                    if (removable) {
                                                        add(
                                                            CustomAccessibilityAction(removeLabel) {
                                                                remove()
                                                                true
                                                            },
                                                        )
                                                    }
                                                    if (canMoveEarlier) {
                                                        add(
                                                            CustomAccessibilityAction(moveUp) {
                                                                queue.moveItem(
                                                                    index,
                                                                    index - 1,
                                                                )
                                                                true
                                                            },
                                                        )
                                                    }
                                                    if (canMoveLater) {
                                                        add(
                                                            CustomAccessibilityAction(moveDown) {
                                                                queue.moveItem(
                                                                    index,
                                                                    index + 1,
                                                                )
                                                                true
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(
                                            horizontal = 4.dp,
                                            vertical = 12.dp,
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (editable) {
                                            val interaction =
                                                remember { MutableInteractionSource() }
                                            IconButton(
                                                onClick = {},
                                                modifier = Modifier.size(
                                                    48.dp,
                                                ).testTag("queue_drag_$index").draggableHandle(
                                                    interactionSource = interaction,
                                                    onDragStarted = {
                                                        haptics.performHapticFeedback(
                                                            dragStartFeedback,
                                                        )
                                                    },
                                                    onDragStopped = {
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.GestureEnd,
                                                        )
                                                    },
                                                ),
                                                interactionSource = interaction,
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DragIndicator,
                                                    stringResource(R.string.reorder),
                                                )
                                            }
                                        } else {
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Box(
                                            Modifier.size(
                                                artworkSize,
                                            ).testTag("queue_artwork_$index"),
                                        ) {
                                            PlaybackArtwork(
                                                snapshot.copy(
                                                    item = item,
                                                    metadata = if (current) {
                                                        snapshot.metadata
                                                    } else {
                                                        item.mediaMetadata
                                                    },
                                                ),
                                                Modifier.fillMaxSize(),
                                                RoundedCornerShape(8.dp),
                                            )
                                            Text(
                                                queue.getDurationAt(index)?.let {
                                                    Utils.formatDurationMillis(it)
                                                }
                                                    ?: "—",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                color = Color.White,
                                                modifier = Modifier.align(
                                                    Alignment.BottomEnd,
                                                ).padding(3.dp)
                                                    .background(
                                                        Color.Black.copy(alpha = .8f),
                                                        RoundedCornerShape(4.dp),
                                                    )
                                                    .padding(
                                                        horizontal = 3.dp,
                                                        vertical = 1.dp,
                                                    )
                                                    .testTag("queue_duration_$index"),
                                            )
                                        }
                                        Column(
                                            Modifier.weight(1f).padding(
                                                start = 16.dp,
                                                end = 8.dp,
                                            ),
                                        ) {
                                            Text(
                                                item.mediaMetadata.title?.toString()
                                                    ?: item.localConfiguration?.uri?.lastPathSegment
                                                    ?: stringResource(R.string.unknown),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (current) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                },
                                                color = if (current) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                            Text(
                                                item.mediaMetadata.subtitle?.toString()
                                                    ?: item.localConfiguration?.uri?.path
                                                        ?.substringBeforeLast(
                                                            '/',
                                                        )?.substringAfterLast('/')
                                                    ?: stringResource(R.string.background_play),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (removable && rowFocused) {
                                            IconButton(onClick = remove) {
                                                Icon(
                                                    painterResource(R.drawable.ic_close),
                                                    stringResource(R.string.remove),
                                                )
                                            }
                                        } else {
                                            Spacer(Modifier.width(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = removal != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(
                start = 16.dp,
                end = 16.dp,
                bottom = (if (showHeader) 96.dp else 16.dp) +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.widthIn(max = 480.dp).testTag("queue_removal_undo"),
            ) {
                Row(
                    Modifier.padding(
                        start = 16.dp,
                        end = 4.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        removal?.item?.mediaMetadata?.title?.toString()
                            ?: removal?.item?.localConfiguration?.uri?.lastPathSegment.orEmpty(),
                        Modifier.weight(
                            1f,
                            fill = false,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.queue_video_removed),
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .7f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = { queue.undoRemoval() },
                        enabled = editable,
                        modifier = Modifier.tvFocusRing(CircleShape),
                    ) {
                        Text(
                            stringResource(R.string.undo),
                            color = MaterialTheme.colorScheme.inversePrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
