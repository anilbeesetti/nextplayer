package com.graviton.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen

@Composable
fun rememberMetadataState(player: Player): MetadataState {
    val metadataState = remember { MetadataState(player) }
    LaunchedEffect(player) { metadataState.observe() }
    return metadataState
}

@Stable
class MetadataState(private val player: Player) {
    var title: String? by mutableStateOf(null)
        private set

    /**
     * The media id of the item being played.
     *
     * Graviton sets the media id to the source URI, so this is the stable key that per-file state
     * such as bookmarks and chapters is stored against.
     */
    var mediaId: String? by mutableStateOf(null)
        private set

    suspend fun observe() {
        update()
        player.listen { events ->
            if (events.containsAny(Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                update()
            }
        }
    }

    private fun update() {
        title = player.mediaMetadata.title?.toString()
        mediaId = player.currentMediaItem?.mediaId
    }
}
