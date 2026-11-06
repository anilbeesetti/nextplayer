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

    fun clearSavedQueue() {
        folderQueue = emptyList()
    }

    override fun transferState(source: Player, target: Player) {
        val state = PlayerTransferState.fromPlayer(source)
        val items = state.mediaItems
        if (target === remote) {
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
