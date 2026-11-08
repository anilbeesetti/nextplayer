package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

internal class PlaybackOutput(context: Context, localPlayer: ExoPlayer, autoplay: () -> Boolean) {
    val player: Player = localPlayer
    fun onNewQueue() = Unit
    fun onQueueReady() = Unit
    fun onStop() = Unit
    fun release() = player.release()
}
