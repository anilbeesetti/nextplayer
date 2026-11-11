package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.google.common.util.concurrent.ListenableFuture

/**
 * A Cast receiver reports the item it plays at once, but Media3 rebuilds its cast queue only when
 * the receiver answers a separate queue request, which some receivers do seconds later. Until
 * then Media3 names another queue entry, so this presents the queue on the receiver's item for
 * every reader of the session: the overlay, the queue, the seek bar and the media notification.
 */
@UnstableApi
internal class ReceiverSyncedPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {
    private var queue: List<MediaItem> = emptyList()
    private var index = C.INDEX_UNSET
    private var durationMs = C.TIME_UNSET

    /** Shows [queue] playing at [index] until called with an empty queue. */
    fun showReceiver(queue: List<MediaItem>, index: Int, durationMs: Long) {
        if (queue == this.queue && index == this.index && durationMs == this.durationMs) return
        this.queue = queue
        this.index = index
        this.durationMs = durationMs
        invalidateState()
    }

    private val showing get() = index in queue.indices

    override fun getState(): State {
        val state = super.getState()
        if (!showing) return state
        val playlist = queue.mapIndexed { itemIndex, item ->
            val itemDurationMs = if (itemIndex == index && durationMs > 0) {
                durationMs
            } else {
                item.mediaMetadata.durationMs
            }
            MediaItemData.Builder("receiver:$itemIndex:${item.mediaId}")
                .setMediaItem(item)
                .setDurationUs(itemDurationMs?.let(Util::msToUs) ?: C.TIME_UNSET)
                .build()
        }
        // Queue positions are not the receiver's yet, so only the playing item can be controlled.
        val commands = state.availableCommands.buildUpon().removeAll(
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
        ).build()
        return state.buildUpon()
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(index)
            .setCurrentAd(
                C.INDEX_UNSET,
                C.INDEX_UNSET,
            )
            .setAvailableCommands(commands)
            .build()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> = if (showing) {
        super.handleSeek(
            player.currentMediaItemIndex,
            positionMs,
            seekCommand,
        )
    } else {
        super.handleSeek(
            mediaItemIndex,
            positionMs,
            seekCommand,
        )
    }
}
