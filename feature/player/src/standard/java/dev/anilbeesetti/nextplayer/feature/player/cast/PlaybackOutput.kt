package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.service.playbackTransferState

internal class PlaybackOutput(
    context: Context,
    localPlayer: ExoPlayer,
    autoplay: () -> Boolean,
    onPresentationChanged: (PlaybackPresentation) -> Unit,
) {
    val player: Player = localPlayer
    fun onNewQueue() = Unit
    fun onQueueReady(mediaId: String? = null) = Unit
    fun onStop() = Unit
    fun cancelConnection() = Unit
    fun retry() = Unit
    fun playOnPhone() = Unit
    fun snapshotForPhone() = playbackTransferState(player)
    fun restoreOnPhone(state: PlayerTransferState) {
        state.setToPlayer(player)
        player.prepare()
    }
    fun release() = player.release()
}
