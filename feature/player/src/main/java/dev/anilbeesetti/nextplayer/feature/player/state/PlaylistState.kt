package dev.anilbeesetti.nextplayer.feature.player.state

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.observeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Window uids are not unique: a MediaController masks each unconfirmed window with uid 0. */
private fun Timeline.queueKeys(): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return List(windowCount) { index ->
        val uid = getWindow(
            index,
            Timeline.Window(),
        ).uid.toString()
        val occurrence = occurrences[uid] ?: 0
        occurrences[uid] = occurrence + 1
        if (occurrence == 0) uid else "$uid#$occurrence"
    }
}

@UnstableApi
@Composable
fun rememberPlaylistState(player: Player): PlaylistState {
    val playlistState = remember(player) { PlaylistState(player = player) }
    LaunchedEffect(key1 = player) {
        withContext(Dispatchers.Main.immediate) { playlistState.observe() }
    }
    return playlistState
}

@UnstableApi
class PlaylistState(private val player: Player?) {
    private var canGetTimeline: Boolean = false
    private var canGetMetadata: Boolean = false

    var timeline: Timeline by mutableStateOf(Timeline.EMPTY)
        private set

    var currentMediaItemIndex by mutableIntStateOf(C.INDEX_UNSET)
        private set

    private val keys by derivedStateOf { timeline.queueKeys() }

    val mediaItemCount: Int
        get() = if (canGetTimeline) timeline.windowCount else 0

    private var pendingRemoval by mutableStateOf<QueueRemovalUndo?>(null)

    internal val removalUndo: QueueRemovalUndo?
        get() = pendingRemoval?.takeIf { saved ->
            SystemClock.elapsedRealtime() < saved.deadline &&
                player?.isCommandAvailable(Player.COMMAND_GET_TIMELINE) == true &&
                liveKeys(player).any { it in saved.remainingKeys }
        }

    internal fun clearRemovalUndo() {
        pendingRemoval = null
    }

    internal fun undoRemoval(): Boolean {
        val playback = player ?: return false
        val saved = removalUndo
        pendingRemoval = null
        if (saved == null ||
            !playback.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        ) {
            return false
        }
        val keys = liveKeys(playback)
        val following = keys.indexOf(saved.nextKey)
        val previous = keys.indexOf(saved.previousKey)
        val insertion = when {
            following >= 0 -> following
            previous >= 0 -> previous + 1
            else -> saved.index.coerceAtMost(keys.size)
        }
        playback.addMediaItem(
            insertion,
            saved.item,
        )
        return true
    }

    private fun liveKeys(playback: Player): List<String> = playback.currentTimeline.queueKeys()

    private var currentDuration by mutableLongStateOf(C.TIME_UNSET)

    var playlistMetadata: MediaMetadata by mutableStateOf(MediaMetadata.EMPTY)
        private set

    private val playerStateObserver = player?.observeState(
        Player.EVENT_PLAYBACK_STATE_CHANGED,
        Player.EVENT_MEDIA_METADATA_CHANGED,
        Player.EVENT_TIMELINE_CHANGED,
        Player.EVENT_POSITION_DISCONTINUITY,
        Player.EVENT_PLAYLIST_METADATA_CHANGED,
        Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
    ) { player ->
        canGetTimeline = player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)
        canGetMetadata = player.isCommandAvailable(Player.COMMAND_GET_METADATA)
        currentDuration = if (player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
            player.duration
        } else {
            C.TIME_UNSET
        }

        playlistMetadata = if (canGetMetadata) player.playlistMetadata else MediaMetadata.EMPTY
        timeline = if (canGetTimeline) player.currentTimeline else Timeline.EMPTY
        currentMediaItemIndex =
            if (canGetTimeline) player.currentMediaItemIndex else C.INDEX_UNSET
        if (pendingRemoval != null && removalUndo == null) pendingRemoval = null
    }

    suspend fun observe() {
        playerStateObserver?.observe()
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (player?.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) != true) return
        if (fromIndex == toIndex) return
        if (fromIndex !in 0 until timeline.windowCount ||
            toIndex !in 0 until timeline.windowCount
        ) {
            return
        }

        player?.moveMediaItem(
            fromIndex,
            toIndex,
        )
    }

    fun indexOfKey(key: String): Int = keys.indexOf(key)

    suspend fun moveItemAndAwait(fromKey: String, toKey: String) {
        val from = indexOfKey(fromKey)
        val to = indexOfKey(toKey)
        if (from == C.INDEX_UNSET || to == C.INDEX_UNSET ||
            player?.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) != true
        ) {
            return
        }
        moveItem(
            from,
            to,
        )
        // Reorderable must see the updated order before accepting the next drag move.
        withTimeoutOrNull(1_000) {
            snapshotFlow { indexOfKey(fromKey) }.first { it == to || it == C.INDEX_UNSET }
        }
    }

    fun canRemoveItem(index: Int): Boolean {
        val playback = player ?: return false
        val count = playback.mediaItemCount
        return playback.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) &&
            count > 1 && index in 0 until count &&
            (
                index != count - 1 || playback.currentMediaItemIndex != index ||
                    playback.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                )
    }

    fun removeItem(index: Int) {
        val playback = player ?: return
        if (!canRemoveItem(index)) return
        val keys = liveKeys(playback)
        val saved = QueueRemovalUndo(
            playback.getMediaItemAt(index),
            index,
            keys.getOrNull(index - 1),
            keys.getOrNull(index + 1),
            keys.filterIndexed { i, _ -> i != index }.toSet(),
            SystemClock.elapsedRealtime() + 4_000,
        )
        if (index == playback.currentMediaItemIndex && index == playback.mediaItemCount - 1) {
            // Removing the current final window otherwise ends playback with earlier items left.
            playback.seekToDefaultPosition(index - 1)
        }
        playback.removeMediaItem(index)
        pendingRemoval = saved
    }

    fun seekToItem(index: Int) {
        if (player?.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) != true) return
        if (index !in 0 until timeline.windowCount) return
        if (player?.currentMediaItemIndex == index) return
        player?.seekToDefaultPosition(index)
    }

    fun getKeyAt(index: Int): String = keys[index]

    fun getDurationAt(index: Int): Long? {
        val window = timeline.getWindow(
            index,
            Timeline.Window(),
        )
        return window.mediaItem.mediaMetadata.durationMs?.takeIf { it > 0 }
            ?: window.durationMs.takeIf { it > 0 }
            ?: currentDuration.takeIf { index == currentMediaItemIndex && it > 0 }
    }

    fun getMediaItemAt(index: Int): MediaItem {
        if (!canGetTimeline || index < 0 || index >= timeline.windowCount) {
            throw IndexOutOfBoundsException()
        }
        val window = Timeline.Window()
        return timeline.getWindow(
            index,
            window,
        ).mediaItem
    }
}

internal data class QueueRemovalUndo(
    val item: MediaItem,
    val index: Int,
    val previousKey: String?,
    val nextKey: String?,
    val remainingKeys: Set<String>,
    val deadline: Long,
)
