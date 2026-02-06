package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerPreferences(
    val resume: Resume = Resume.YES,
    val rememberPlayerBrightness: Boolean = false,
    val playerBrightness: Float = 0.5f,
    val minDurationForFastSeek: Long = 120000L,
    val rememberSelections: Boolean = true,
    val playerScreenOrientation: ScreenOrientation = ScreenOrientation.VIDEO_ORIENTATION,
    val playerVideoZoom: VideoContentScale = VideoContentScale.BEST_FIT,
    val defaultPlaybackSpeed: Float = 1.0f,
    val persistentPlaybackSpeed: Boolean = false,
    val autoplay: Boolean = true,
    val autoPip: Boolean = true,
    val autoBackgroundPlay: Boolean = false,
    val loopMode: LoopMode = LoopMode.OFF,

    // Controls (Gestures)
    @Deprecated(message = "Use individual enableVolumeSwipeGesture and enableBrightnessSwipeGesture instead")
    val useSwipeControls: Boolean = true,
    val enableVolumeSwipeGesture: Boolean = true,
    val enableBrightnessSwipeGesture: Boolean = true,
    val useSeekControls: Boolean = true,
    val useZoomControls: Boolean = true,
    val enablePanGesture: Boolean = false,
    val doubleTapGesture: DoubleTapGesture = DoubleTapGesture.BOTH,
    val useLongPressControls: Boolean = false,
    val longPressControlsSpeed: Float = 2.0f,
    val seekIncrement: Int = DEFAULT_SEEK_INCREMENT,
    val seekSensitivity: Float = DEFAULT_SEEK_SENSITIVITY,

    // Player Interface
    val controllerAutoHideTimeout: Int = DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT,
    val controlButtonsPosition: ControlButtonsPosition = ControlButtonsPosition.LEFT,
    val hidePlayerButtonsBackground: Boolean = false,

    // Audio Preferences
    val preferredAudioLanguage: String = "",
    val pauseOnHeadsetDisconnect: Boolean = true,
    val requireAudioFocus: Boolean = true,
    val showSystemVolumePanel: Boolean = true,
    val enableVolumeBoost: Boolean = false,

    // Subtitle Preferences
    val useSystemCaptionStyle: Boolean = false,
    val preferredSubtitleLanguage: String = "",
    val subtitleTextEncoding: String = "",
    val subtitleTextSize: Int = DEFAULT_SUBTITLE_TEXT_SIZE,
    val subtitleBackground: Boolean = false,
    val subtitleFont: Font = Font.DEFAULT,
    val subtitleTextBold: Boolean = true,
    val applyEmbeddedStyles: Boolean = true,

    // Decoder Preferences
    val decoderPriority: DecoderPriority = DecoderPriority.PREFER_DEVICE,
) {

    companion object {
        const val DEFAULT_SEEK_INCREMENT = 10
        const val DEFAULT_SEEK_SENSITIVITY = 0.5f
        const val DEFAULT_SUBTITLE_TEXT_SIZE = 20
        const val DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT = 4
    }
}
