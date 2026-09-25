package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.PlayerStateObserver
import androidx.media3.ui.compose.state.observeState

@OptIn(UnstableApi::class)
@Composable
fun rememberMetadataState(player: Player?): MetadataState {
    val metadataState = remember(player) { MetadataState(player) }
    LaunchedEffect(player) { metadataState.observe() }
    return metadataState
}

@UnstableApi
@Stable
class MetadataState(private val player: Player?) {
    var mediaId: String? by mutableStateOf(null)
        private set

    var title: String? by mutableStateOf(null)
        private set

    private val playerStateObserver: PlayerStateObserver? =
        player?.observeState(
            Player.EVENT_MEDIA_METADATA_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
        ) {
            mediaId = player.currentMediaItem?.mediaId
            title = player.mediaMetadata.title?.toString()
        }

    suspend fun observe() {
        mediaId = player?.currentMediaItem?.mediaId
        title = player?.mediaMetadata?.title?.toString()
        playerStateObserver?.observe()
    }
}
