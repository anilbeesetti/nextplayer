package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@UnstableApi
fun PlayerView.togglePlayPause() {
    this.controllerAutoShow = this.isControllerFullyVisible
    if (this.player?.isPlaying == true) {
        this.player?.pause()
    } else {
        this.player?.play()
    }
}
