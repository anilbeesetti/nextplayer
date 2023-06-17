package dev.anilbeesetti.nextplayer.feature.player.extensions

import dev.anilbeesetti.nextplayer.core.model.FastSeek
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences

fun PlayerPreferences.shouldFastSeekDisable(duration: Long): Boolean {
    if (fastSeek != FastSeek.AUTO) return false
    return duration < minDurationForFastSeek
}
