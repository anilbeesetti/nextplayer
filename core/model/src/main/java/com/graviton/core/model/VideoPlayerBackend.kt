package com.graviton.core.model

import kotlinx.serialization.Serializable

/** Video-only backend selection. Music always uses the service-owned Media3 audio pipeline. */
@Serializable
enum class VideoPlayerBackend {
    MPV_RX,
    MPV_REX,
    GRAVITON,
}
