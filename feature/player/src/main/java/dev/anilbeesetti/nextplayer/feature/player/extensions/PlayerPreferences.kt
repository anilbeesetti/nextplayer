package dev.anilbeesetti.nextplayer.feature.player.extensions

import dev.anilbeesetti.nextplayer.core.model.FastSeek
import dev.anilbeesetti.nextplayer.core.model.PlayerPrefs

fun PlayerPrefs.shouldFastSeek(duration: Long): Boolean {
    return when (fastSeek) {
        FastSeek.ENABLE -> true
        FastSeek.DISABLE -> false
        FastSeek.AUTO -> duration >= minDurationForFastSeek
    }
}
