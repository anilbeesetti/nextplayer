package dev.anilbeesetti.nextplayer.core.datastore

import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.FastSeek
import dev.anilbeesetti.nextplayer.core.model.Resume
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
    val preferredSubtitleLanguage: String = ""
)
