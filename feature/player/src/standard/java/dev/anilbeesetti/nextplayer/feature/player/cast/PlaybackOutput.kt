package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.service.playbackTransferState

internal class PlaybackOutput(
    context: Context,
    localPlayer: ExoPlayer,
    autoplay: () -> Boolean,
    private val onPresentationChanged: (PlaybackPresentation) -> Unit,
) {
    val player: Player = localPlayer
    val hasPendingPlayback: Boolean get() = false
    fun onNewQueue() = Unit
    fun onQueueReady(mediaId: String? = null, pendingState: PlayerTransferState? = null) =
        onPresentationChanged(PlaybackPresentation())
    fun onStop() = onPresentationChanged(PlaybackPresentation(PlaybackConnection.STOPPED))
    fun cancelConnection() = Unit
    fun retry() = Unit
    fun playOnPhone() = Unit
    fun snapshotForPhone() = playbackTransferState(player)
    fun restoreOnPhone(state: PlayerTransferState) {
        state.setToPlayer(player)
        player.prepare()
        onPresentationChanged(PlaybackPresentation())
    }
    fun release() = player.release()
}
