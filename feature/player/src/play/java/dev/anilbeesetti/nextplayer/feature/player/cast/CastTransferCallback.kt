package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.media3.cast.CastPlayer
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.withoutResumePosition
import dev.anilbeesetti.nextplayer.feature.player.service.mediaItemsForCast
import dev.anilbeesetti.nextplayer.feature.player.service.playbackTransferState

@UnstableApi
internal class CastTransferCallback(
    private val remote: Player,
    private val autoplay: () -> Boolean,
) : CastPlayer.TransferCallback {
    private var folderQueue = emptyList<MediaItem>()
    private var stopping = false

    fun retainFolderQueue(items: List<MediaItem>) {
        folderQueue = items
    }

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

    fun snapshotForPhone(source: Player): PlayerTransferState {
        val state = playbackTransferState(source)
        val index = folderQueue.indexOfFirst { it.mediaId == source.currentMediaItem?.mediaId }
        return if (index >= 0 && state.mediaItems.size == 1) {
            state.buildUpon().setMediaItems(
                folderQueue.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) item.withoutResumePosition() else item
                },
            ).setCurrentMediaItemIndex(index).build()
        } else {
            state
        }
    }

    override fun transferState(source: Player, target: Player) {
        if (target !== remote && stopping) {
            stopping = false
            target.pause()
            target.clearMediaItems()
            target.stop()
            return
        }
        val state = playbackTransferState(source)
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
                snapshotForPhone(source).setToPlayer(target)
            } else {
                // Keep Media3's filtering of receiver placeholders without a playback URI.
                CastPlayer.TransferCallback.DEFAULT.transferState(
                    object : ForwardingPlayer(source) {
                        override fun getMediaItemAt(index: Int): MediaItem = state.mediaItems[index]
                    },
                    target,
                )
            }
            folderQueue = emptyList()
        }
    }
}
