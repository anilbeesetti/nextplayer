package dev.anilbeesetti.nextplayer.core.model

data class PlayerPrefs(
    val resume: Resume,
    val rememberPlayerBrightness: Boolean,
    val playerBrightness: Float,
    val doubleTapGesture: DoubleTapGesture,
    val fastSeek: FastSeek,
    val minDurationForFastSeek: Long,
    val useSwipeControls: Boolean,
    val useSeekControls: Boolean,
    val rememberSelections: Boolean,
    val preferredAudioLanguage: String,
    val preferredSubtitleLanguage: String,
    val playerScreenOrientation: ScreenOrientation
) {

    companion object {
        fun default() = PlayerPrefs(
            resume = Resume.YES,
            rememberPlayerBrightness = false,
            playerBrightness = 0.5f,
            doubleTapGesture = DoubleTapGesture.FAST_FORWARD_AND_REWIND,
            fastSeek = FastSeek.AUTO,
            minDurationForFastSeek = 120000L,
            useSwipeControls = true,
            useSeekControls = true,
            rememberSelections = true,
            preferredAudioLanguage = "",
            preferredSubtitleLanguage = "",
            playerScreenOrientation = ScreenOrientation.VIDEO_ORIENTATION
        )
    }
}
