package com.graviton.feature.player.backend

import android.content.Context
import android.util.AttributeSet
import com.graviton.core.model.VideoPlayerBackend
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib

/** Small, lifecycle-safe libmpv surface. Profiles alter policy, not the surrounding player UI. */
class GravitonMpvView(context: Context, attrs: AttributeSet) : BaseMPVView(context, attrs) {
    var backendKind: VideoPlayerBackend = VideoPlayerBackend.MPV_REX

    override fun initOptions() {
        MPVLib.setOptionString("profile", if (backendKind == VideoPlayerBackend.MPV_RX) "fast" else "gpu-hq")
        setVo(if (backendKind == VideoPlayerBackend.MPV_REX) "gpu-next" else "gpu")
        MPVLib.setOptionString("hwdec", "mediacodec-copy,no")
        MPVLib.setOptionString("hwdec-codecs", "all")
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("demuxer-max-bytes", if (backendKind == VideoPlayerBackend.MPV_RX) "64MiB" else "128MiB")
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("audio-focus", "yes")
        MPVLib.setOptionString("keep-open", "yes")
    }

    override fun postInitOptions() = Unit

    override fun observeProperties() {
        MPVLib.observeProperty("pause", 3)
        MPVLib.observeProperty("time-pos", 5)
        MPVLib.observeProperty("duration", 5)
    }
}
