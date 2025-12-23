package dev.anilbeesetti.nextplayer.feature.player.extensions

import kotlin.time.Duration

fun Duration.formatted(): String = toComponents { hours, minutes, seconds, nanoseconds ->
    if (hours > 0) {
        "$hours:${minutes.padStartWith0()}:${seconds.padStartWith0()}"
    } else {
        "${minutes.padStartWith0()}:${seconds.padStartWith0()}"
    }
}

private fun Number.padStartWith0() = this.toString().padStart(2, '0')
