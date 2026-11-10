package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.withoutResumePosition

internal enum class PlaybackConnection { LOCAL, CONNECTING, CAST, LOST }

internal data class PlaybackPresentation(
    val connection: PlaybackConnection = PlaybackConnection.LOCAL,
    val deviceName: String? = null,
    val expansionRequest: Long = 0,
) {
    fun writeTo(bundle: Bundle) {
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
    }

    companion object {
        private const val CONNECTION = "playback_connection"
        private const val DEVICE_NAME = "playback_device_name"
        private const val EXPANSION_REQUEST = "playback_expansion_request"
        fun from(bundle: Bundle) = PlaybackPresentation(
            connection = PlaybackConnection.entries.find { it.name == bundle.getString(CONNECTION) }
                ?: PlaybackConnection.LOCAL,
            deviceName = bundle.getString(DEVICE_NAME),
            expansionRequest = bundle.getLong(EXPANSION_REQUEST),
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
