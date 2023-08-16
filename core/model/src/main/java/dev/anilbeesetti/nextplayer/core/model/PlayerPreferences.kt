package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerPreferences(
    val resume: Resume = Resume.YES,
    val rememberPlayerBrightness: Boolean = false,
    val playerBrightness: Float = 0.5f,
    val doubleTapGesture: DoubleTapGesture = DoubleTapGesture.FAST_FORWARD_AND_REWIND,
    val fastSeek: FastSeek = FastSeek.AUTO,
    val minDurationForFastSeek: Long = 120000L,
    val useSwipeControls: Boolean = true,
    val useSeekControls: Boolean = true,
    val rememberSelections: Boolean = true,
    val preferredAudioLanguage: String = "",
    val playerScreenOrientation: ScreenOrientation = ScreenOrientation.VIDEO_ORIENTATION,
    val playerVideoZoom: VideoZoom = VideoZoom.BEST_FIT,
    val defaultPlaybackSpeed: Float = 1.0f,
    val controllerAutoHideTimeout: Int = 2,
    val seekIncrement: Int = 10,
    val autoplay: Boolean = true,

    // Subtitle Preferences
    val preferredSubtitleLanguage: String = "",
    val subtitleTextEncoding: String = "",
    val subtitleTextSize: Int = 23,
    val subtitleBackground: Boolean = false,
    val subtitleFont: Font = Font.DEFAULT,
    val subtitleTextBold: Boolean = true,
    val applyEmbeddedStyles: Boolean = true,

    // Decoder Preferences
    val decoderPriority: DecoderPriority = DecoderPriority.PREFER_DEVICE
)
