package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.runtime.staticCompositionLocalOf
import dev.anilbeesetti.nextplayer.core.common.BuildConfig
import dev.anilbeesetti.nextplayer.core.model.ArtworkStyle

internal val LocalControlsArtworkAspectRatio =
    staticCompositionLocalOf { PlaybackArtworkAspectRatio }
internal val LocalQueueArtworkAspectRatio = staticCompositionLocalOf { PlaybackArtworkAspectRatio }

internal fun artworkAspectRatio(style: ArtworkStyle, debug: Boolean = BuildConfig.DEBUG): Float =
    if (debug && style == ArtworkStyle.AUDIO) 1f else PlaybackArtworkAspectRatio
