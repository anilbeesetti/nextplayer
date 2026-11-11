package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal data class PlaybackSnapshot(
    val item: MediaItem?,
    val metadata: MediaMetadata,
    val playbackState: Int,
    val playWhenReady: Boolean,
    val deviceInfo: DeviceInfo,
    val duration: Long,
    val position: Long,
    val commands: Player.Commands,
    val deviceVolume: Int,
    val volume: Float,
    val shuffle: Boolean,
    val repeatMode: Int,
) {
    val visible: Boolean get() = item != null && playbackState != Player.STATE_ENDED
    val isRemote: Boolean get() = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
    val progress: Float get() = if (duration > 0) {
        (position.toDouble() / duration).toFloat().coerceIn(
            0f,
            1f,
        )
    } else {
        0f
    }

    fun retainedFor(
        presentation: PlaybackPresentation,
        previous: PlaybackSnapshot,
    ): PlaybackSnapshot {
        val casting = presentation.mediaItem
        return when {
            presentation.connection == PlaybackConnection.STOPPED -> copy(item = null)

            // Until it confirms the cast, the receiver can report another entry of the queue.
            presentation.connection == PlaybackConnection.CONNECTING && casting != null &&
                item?.mediaId != casting.mediaId ->
                if (previous.visible && previous.item?.mediaId == casting.mediaId) {
                    previous
                } else {
                    retainedItem(presentation)
                }

            // Casting, the receiver's playback is live but Media3 can still name another entry.
            presentation.connection == PlaybackConnection.CAST && presentation.receiverAhead &&
                visible && casting != null && item?.mediaId != casting.mediaId -> {
                val known = previous.item?.mediaId == casting.mediaId
                copy(
                    item = casting,
                    metadata = if (known) previous.metadata else casting.mediaMetadata,
                    duration = casting.mediaMetadata.durationMs
                        ?: if (known) previous.duration else duration,
                )
            }

            (!visible || item?.localConfiguration == null) && presentation.connection in setOf(
                PlaybackConnection.CONNECTING,
                PlaybackConnection.CAST,
                PlaybackConnection.LOST,
            ) -> if (previous.visible && previous.item?.localConfiguration != null) {
                previous
            } else {
                retainedItem(presentation)
            }

            else -> this
        }
    }

    private fun retainedItem(presentation: PlaybackPresentation) = copy(
        item = presentation.mediaItem,
        playbackState = Player.STATE_IDLE,
        metadata = presentation.mediaItem?.mediaMetadata ?: metadata,
        position = presentation.position,
        duration = presentation.mediaItem?.mediaMetadata?.durationMs ?: 0,
    )

    companion object {
        fun from(player: Player) = PlaybackSnapshot(
            item = player.currentMediaItem,
            metadata = player.mediaMetadata,
            playbackState = player.playbackState,
            playWhenReady = player.playWhenReady,
            deviceInfo = player.deviceInfo,
            duration = player.duration,
            position = player.currentPosition.coerceAtLeast(0),
            commands = player.availableCommands,
            volume = player.volume,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            deviceVolume = if (player.isCommandAvailable(Player.COMMAND_GET_DEVICE_VOLUME)) {
                player.deviceVolume
            } else {
                0
            },
        )
    }
}

@Composable
internal fun rememberPlaybackSnapshot(player: Player): PlaybackSnapshot {
    var snapshot by remember(player) { mutableStateOf(PlaybackSnapshot.from(player)) }
    DisposableEffect(player) {
        snapshot = PlaybackSnapshot.from(player)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                snapshot = PlaybackSnapshot.from(player)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (isActive) {
            delay(500)
            snapshot = PlaybackSnapshot.from(player)
        }
    }
    return snapshot
}
