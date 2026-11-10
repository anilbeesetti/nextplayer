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
        // The transfer carries live position; a late receiver event must not restore an old hint.
        val transferSource = object : ForwardingPlayer(source) {
            override fun getMediaItemAt(index: Int): MediaItem = source.getMediaItemAt(index).let {
                if (index == source.currentMediaItemIndex) it.withoutResumePosition() else it
            }
        }
        val state = PlayerTransferState.fromPlayer(transferSource)
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
                val queue = folderQueue.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) item.withoutResumePosition() else item
                }
                state.buildUpon().setMediaItems(queue)
                    .setCurrentMediaItemIndex(index).build().setToPlayer(target)
            } else {
                CastPlayer.TransferCallback.DEFAULT.transferState(
                    transferSource,
                    target,
                )
            }
            folderQueue = emptyList()
        }
    }
}
