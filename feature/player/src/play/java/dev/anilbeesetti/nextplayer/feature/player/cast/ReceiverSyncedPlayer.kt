package dev.anilbeesetti.nextplayer.feature.player.cast

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
    private val artwork = mutableMapOf<String, Uri>()

    /** The receiver reports its own metadata, without the phone's thumbnail of the item. */
    fun showArtwork(mediaId: String, uri: Uri) {
        if (artwork.put(
                mediaId,
                uri,
            ) != uri
        ) {
            invalidateState()
        }
    }

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
        if (!showing) return state.withArtwork()
        val playlist = queue.mapIndexed { itemIndex, item ->
            val itemDurationMs = if (itemIndex == index && durationMs > 0) {
                durationMs
            } else {
                item.mediaMetadata.durationMs
            }
            MediaItemData.Builder("receiver:$itemIndex:${item.mediaId}")
                .setMediaItem(item)
                .setMediaMetadata(item.mediaMetadata.withArtwork(item))
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

    private fun State.withArtwork(): State {
        if (deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE ||
            currentMediaItemIndex !in 0 until timeline.windowCount
        ) {
            return this
        }
        val item = timeline.getWindow(
            currentMediaItemIndex,
            Timeline.Window(),
        ).mediaItem
        val metadata = currentMetadata ?: item.mediaMetadata
        val withArtwork = metadata.withArtwork(item)
        if (withArtwork === metadata) return this
        return buildUpon().setPlaylist(
            timeline,
            currentTracks,
            withArtwork,
        ).build()
    }

    // The receiver echoes the placeholder artwork it was sent, so the phone's thumbnail wins.
    private fun MediaMetadata.withArtwork(item: MediaItem): MediaMetadata {
        val uri = artwork[item.mediaId]
            ?: item.mediaMetadata.artworkUri.takeIf { artworkUri == null && artworkData == null }
            ?: return this
        if (uri == artworkUri) return this
        return buildUpon().setArtworkUri(uri).setArtworkData(
            null,
            null,
        ).build()
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
