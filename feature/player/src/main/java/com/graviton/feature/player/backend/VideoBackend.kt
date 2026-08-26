package com.graviton.feature.player.backend

import androidx.media3.common.Player
import com.graviton.core.model.VideoPlayerBackend
import `is`.xyz.mpv.MPVLib

/** Commands shared by every video engine. UI and navigation do not depend on an engine API. */
interface VideoBackend {
    val kind: VideoPlayerBackend
    fun load(uri: String, startPositionMs: Long = 0L)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun release()
}

class GravitonVideoBackend(private val player: Player) : VideoBackend {
    override val kind = VideoPlayerBackend.GRAVITON
    override fun load(uri: String, startPositionMs: Long) {
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri), startPositionMs)
        player.prepare()
    }
    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)
    override fun release() = Unit // PlayerService owns the Media3 player.
}

/** libmpv uses one process-global native context; the owning activity serializes its lifecycle. */
class MpvVideoBackend(override val kind: VideoPlayerBackend) : VideoBackend {
    init {
        require(kind != VideoPlayerBackend.GRAVITON)
    }

    override fun load(uri: String, startPositionMs: Long) {
        MPVLib.command("loadfile", uri, "replace")
        if (startPositionMs > 0) MPVLib.command("seek", (startPositionMs / 1000.0).toString(), "absolute+exact")
    }
    override fun play() = MPVLib.setPropertyBoolean("pause", false)
    override fun pause() = MPVLib.setPropertyBoolean("pause", true)
    override fun seekTo(positionMs: Long) = MPVLib.command("seek", (positionMs / 1000.0).toString(), "absolute+exact")
    override fun setSpeed(speed: Float) = MPVLib.setPropertyDouble("speed", speed.toDouble())
    override fun release() = Unit // GravitonMpvView owns native destruction.
}
