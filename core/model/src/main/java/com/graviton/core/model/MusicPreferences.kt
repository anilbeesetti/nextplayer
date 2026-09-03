package com.graviton.core.model

import kotlinx.serialization.Serializable

/** Visual variants share one player layout and only alter its presentation tokens. */
@Serializable
enum class NowPlayingStyle {
    CLASSIC,
    EXPRESSIVE,
    BLUR,
    M3,
    PLAIN,
    PEEK,
}

@Serializable
enum class MusicBackgroundStyle {
    THEME,
    ARTWORK,
    BLURRED_ARTWORK,
    BLACK,
}

@Serializable
enum class LyricsSourceKind {
    EMBEDDED,
    SIDECAR_LRC,
    SIDECAR_TTML,
    LRCLIB,
}
