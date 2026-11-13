package dev.anilbeesetti.nextplayer.feature.player.ui

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                                ListItem(
                                    onClick = { queue.seekToItem(queue.indexOfKey(key)) },
                                    enabled = selectable,
                                    verticalAlignment = Alignment.CenterVertically,
                                    shapes = ListItemDefaults.shapes(shape = shape),
                                    colors = ListItemDefaults.colors(
                                        containerColor =
                                            MaterialTheme.colorScheme.surfaceContainerLowest,
                                    ),
                                    contentPadding = PaddingValues(
                                        horizontal = 4.dp,
                                        vertical = 12.dp,
                                    ),
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
                                    leadingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                            PlaybackArtwork(
                                                snapshot.copy(
                                                    item = item,
                                                    metadata = if (current) {
                                                        snapshot.metadata
                                                    } else {
                                                        item.mediaMetadata
                                                    },
                                                ),
                                                Modifier.size(
                                                    60.dp,
                                                ).testTag("queue_artwork_$index"),
                                                RoundedCornerShape(8.dp),
                                            )
                                        }
                                    },
                                    supportingContent = {
                                        Text(
                                            queue.getDurationAt(index)?.let {
                                                Utils.formatDurationMillis(it)
                                            } ?: "—",
                                            Modifier.testTag("queue_duration_$index"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    trailingContent = {
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
                                    },
                                ) {
                                    Text(
                                        item.mediaMetadata.title?.toString()
                                            ?: item.localConfiguration?.uri?.lastPathSegment
                                            ?: stringResource(R.string.unknown),
                                        modifier = Modifier.testTag("queue_title_$index"),
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
            Snackbar(
                modifier = Modifier.widthIn(max = 480.dp).testTag("queue_removal_undo"),
                shape = RoundedCornerShape(16.dp),
                action = {
                    TextButton(
                        onClick = { queue.undoRemoval() },
                        enabled = editable,
                        modifier = Modifier.tvFocusRing(CircleShape),
                    ) {
                        Text(
                            stringResource(R.string.undo),
                            color = SnackbarDefaults.actionColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        removal?.item?.mediaMetadata?.title?.toString()
                            ?: removal?.item?.localConfiguration?.uri?.lastPathSegment.orEmpty(),
                        Modifier.weight(
                            1f,
                            fill = false,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.queue_video_removed),
                        color = SnackbarDefaults.contentColor.copy(alpha = .7f),
                    )
                }
            }
        }
    }
}
