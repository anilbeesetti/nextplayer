package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.PlayerPrefs

fun PlayerPreferences.toPlayerPrefs() = PlayerPrefs(
    resume = resume,
    rememberPlayerBrightness = rememberPlayerBrightness,
    playerBrightness = playerBrightness,
    doubleTapGesture = doubleTapGesture,
    fastSeek = fastSeek,
    minDurationForFastSeek = minDurationForFastSeek,
    useSwipeControls = useSwipeControls,
    useSeekControls = useSeekControls,
    rememberSelections = rememberSelections,
    preferredAudioLanguage = preferredAudioLanguage,
    preferredSubtitleLanguage = preferredSubtitleLanguage,
    playerScreenOrientation = playerScreenOrientation
)
