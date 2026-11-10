package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val reorder = rememberReorderableLazyListState(list) { from, to ->
        queue.moveItem(
            from.index,
            to.index,
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
    Column(modifier.fillMaxSize()) {
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
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    queue.mediaItemCount,
                    key = queue::getKeyAt,
                ) { index ->
                    val item = queue.getMediaItemAt(index)
                    val current = index == queue.currentMediaItemIndex
                    val shape = RoundedCornerShape(if (current) 60.dp else 22.dp)
                    ReorderableItem(
                        reorder,
                        key = queue.getKeyAt(index),
                    ) {
                        Surface(
                            onClick = { queue.seekToItem(index) },
                            enabled = selectable,
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth().tvFocusRing(shape)
                                .semantics {
                                    selected = current
                                    if (editable) {
                                        customActions = buildList {
                                            if (index > 0) {
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
                                            if (index <
                                                queue.mediaItemCount - 1
                                            ) {
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
                                    val interaction = remember { MutableInteractionSource() }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.size(48.dp).draggableHandle(
                                            interactionSource = interaction,
                                            onDragStarted = {
                                                haptics.performHapticFeedback(
                                                    HapticFeedbackType.GestureThresholdActivate,
                                                )
                                            },
                                        ),
                                        interactionSource = interaction,
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_drag_handle),
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
                                    Modifier.size(42.dp),
                                    RoundedCornerShape(if (current) 60.dp else 8.dp),
                                )
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
                                Text(
                                    queue.getDurationAt(index)?.let {
                                        Utils.formatDurationMillis(it)
                                    }
                                        ?: "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("queue_duration_$index"),
                                )
                                if (editable && queue.mediaItemCount > 1) {
                                    IconButton(onClick = { queue.removeItem(index) }) {
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
