package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.observeState

@UnstableApi
@Composable
fun rememberPlaylistState(player: Player): PlaylistState {
    val playlistState = remember(player) { PlaylistState(player) }
    LaunchedEffect(player) { playlistState.observe() }
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

    val mediaItemCount: Int
        get() = if (canGetTimeline) timeline.windowCount else 0

    var playlistMetadata: MediaMetadata by mutableStateOf(MediaMetadata.EMPTY)
        private set


    private val playerStateObserver =
        player?.observeState(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_PLAYLIST_METADATA_CHANGED,
            Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
        ) { player ->
            canGetTimeline = player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)
            canGetMetadata = player.isCommandAvailable(Player.COMMAND_GET_METADATA)

            playlistMetadata = if (canGetMetadata) player.playlistMetadata else MediaMetadata.EMPTY
            timeline = if (canGetTimeline) player.currentTimeline else Timeline.EMPTY
            currentMediaItemIndex = if (canGetTimeline) player.currentMediaItemIndex else C.INDEX_UNSET
        }

    suspend fun observe() {
        playerStateObserver?.observe()
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in 0 until timeline.windowCount || toIndex >= timeline.windowCount) return

        player?.moveMediaItem(fromIndex, toIndex)
    }

    fun removeItem(index: Int) {
        if (index !in 0 until timeline.windowCount) return
        if (timeline.windowCount <= 1) return // Don't remove the last item

        player?.removeMediaItem(index)
    }

    fun seekToItem(index: Int) {
        if (index !in 0 until timeline.windowCount) return
        if (player?.currentMediaItemIndex == index) return
        player?.seekToDefaultPosition(index)
    }

    fun getMediaItemAt(index: Int): MediaItem {
        if (!canGetTimeline || index < 0 || index >= timeline.windowCount) {
            throw IndexOutOfBoundsException()
        }
        val window = Timeline.Window()
        return timeline.getWindow(index, window).mediaItem
    }
}
