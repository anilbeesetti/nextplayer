package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun rememberPlaylistState(player: Player): PlaylistState {
    val playlistState = remember { PlaylistState(player) }
    LaunchedEffect(player) { playlistState.observe() }
    return playlistState
}

class PlaylistState(
    private val player: Player,
) {
    var playlist: List<MediaItem> by mutableStateOf(emptyList())
        private set

    var currentMediaItemIndex: Int by mutableStateOf(0)
        private set

    suspend fun observe() {
        updatePlaylist()
        updateCurrentIndex()

        player.listen { events ->
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) {
                updatePlaylist()
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                updateCurrentIndex()
            }
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in playlist.indices || toIndex !in playlist.indices) return

        player.moveMediaItem(fromIndex, toIndex)
        updatePlaylist()
        updateCurrentIndex()
    }

    fun removeItem(index: Int) {
        if (index !in playlist.indices) return
        if (playlist.size <= 1) return // Don't remove the last item

        player.removeMediaItem(index)
        updatePlaylist()
        updateCurrentIndex()
    }

    fun seekToItem(index: Int) {
        if (index !in playlist.indices) return
        player.seekToDefaultPosition(index)
        updateCurrentIndex()
    }

    private fun updatePlaylist() {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        playlist = items
    }

    private fun updateCurrentIndex() {
        currentMediaItemIndex = player.currentMediaItemIndex
    }
}
