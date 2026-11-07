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
