package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.service.mediaItemsForCast

@UnstableApi
internal class CastTransferCallback(
    private val remote: Player,
    private val autoplay: () -> Boolean,
) : CastPlayer.TransferCallback {
    private var folderQueue = emptyList<MediaItem>()
    private var stopping = false

    fun clearSavedQueue() {
        folderQueue = emptyList()
    }

    fun onStop() {
        stopping = true
        clearSavedQueue()
    }

    fun onQueueReady() {
        stopping = false
    }

    override fun transferState(source: Player, target: Player) {
        if (target !== remote && stopping) {
            stopping = false
            target.pause()
            target.clearMediaItems()
            target.stop()
            return
        }
        val state = PlayerTransferState.fromPlayer(source)
        val items = state.mediaItems
        if (target === remote) {
            stopping = false
            val queue = mediaItemsForCast(
                items,
                state.currentMediaItemIndex,
                autoplay(),
            )
            folderQueue = if (queue.size != items.size) items else emptyList()
            state.buildUpon()
                .setMediaItems(queue)
                .setCurrentMediaItemIndex(
                    if (queue.size == items.size) state.currentMediaItemIndex else 0,
                )
                .build().setToPlayer(target)
            // UI surface cleanup will be forwarded to the remote player after this switch.
            if (source.isCommandAvailable(
                    Player.COMMAND_SET_VIDEO_SURFACE,
                )
            ) {
                source.clearVideoSurface()
            }
        } else {
            val index = folderQueue.indexOfFirst {
                it.mediaId == source.currentMediaItem?.mediaId
            }
            if (index >= 0 && items.size == 1) {
                state.buildUpon().setMediaItems(folderQueue)
                    .setCurrentMediaItemIndex(index).build().setToPlayer(target)
            } else {
                CastPlayer.TransferCallback.DEFAULT.transferState(
                    source,
                    target,
                )
            }
            folderQueue = emptyList()
        }
    }
}
