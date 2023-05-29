package dev.anilbeesetti.nextplayer.feature.player.extensions

import dev.anilbeesetti.nextplayer.core.datastore.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.FastSeek

fun PlayerPreferences.shouldFastSeek(duration: Long): Boolean {
    return when (fastSeek) {
        FastSeek.ENABLE -> true
        FastSeek.DISABLE -> false
        FastSeek.AUTO -> duration >= minDurationForFastSeek
    }
}
