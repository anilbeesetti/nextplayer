package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.withoutResumePosition

internal enum class PlaybackConnection { LOCAL, CONNECTING, CAST, LOST, STOPPED }

internal data class PlaybackPresentation(
    val connection: PlaybackConnection = PlaybackConnection.LOCAL,
    val deviceName: String? = null,
    val expansionRequest: Long = 0,
    val mediaItem: MediaItem? = null,
    val position: Long = 0,
    /** Casting has started, but Media3's queue does not yet name the item the receiver plays. */
    val receiverAhead: Boolean = false,
) {
    fun writeTo(bundle: Bundle) {
        bundle.putBundle(
            MEDIA_ITEM,
            mediaItem?.toBundleIncludeLocalConfiguration(),
        )
        bundle.putLong(
            POSITION,
            position,
        )
        bundle.putString(
            CONNECTION,
            connection.name,
        )
        bundle.putString(
            DEVICE_NAME,
            deviceName,
        )
        bundle.putLong(
            EXPANSION_REQUEST,
            expansionRequest,
        )
        bundle.putBoolean(
            RECEIVER_AHEAD,
            receiverAhead,
        )
    }

    companion object {
        private const val MEDIA_ITEM = "playback_retained_media_item"
        private const val POSITION = "playback_retained_position"
        private const val CONNECTION = "playback_connection"
        private const val DEVICE_NAME = "playback_device_name"
        private const val EXPANSION_REQUEST = "playback_expansion_request"
        private const val RECEIVER_AHEAD = "playback_receiver_ahead"
        fun from(bundle: Bundle) = PlaybackPresentation(
            connection = PlaybackConnection.entries.find { it.name == bundle.getString(CONNECTION) }
                ?: PlaybackConnection.LOCAL,
            deviceName = bundle.getString(DEVICE_NAME),
            expansionRequest = bundle.getLong(EXPANSION_REQUEST),
            mediaItem = bundle.getBundle(MEDIA_ITEM)?.let(MediaItem::fromBundle),
            position = bundle.getLong(POSITION),
            receiverAhead = bundle.getBoolean(RECEIVER_AHEAD),
        )
    }
}

const val ACTION_EXPAND_PLAYBACK = "dev.anilbeesetti.nextplayer.EXPAND_PLAYBACK"
internal const val EXTRA_WATCH_VIDEO = "watch_video"

fun Context.expandedPlaybackIntent(): Intent = Intent(ACTION_EXPAND_PLAYBACK)
    .setClassName(
        packageName,
        "dev.anilbeesetti.nextplayer.MainActivity",
    )
    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@UnstableApi
internal fun playbackTransferState(player: Player): PlayerTransferState =
    PlayerTransferState.fromPlayer(player).let { state ->
        state.buildUpon().setMediaItems(
            state.mediaItems.mapIndexed { index, item ->
                if (index == state.currentMediaItemIndex) item.withoutResumePosition() else item
            },
        ).build()
    }
