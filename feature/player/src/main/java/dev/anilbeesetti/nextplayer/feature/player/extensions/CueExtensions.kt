package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.text.Cue

fun List<Cue>.stackUnpositionedCues(): List<Cue> {
    if (size <= 1) return this

    var nextLine = -1
    return map { cue ->
        val text = cue.text
        if (cue.line != Cue.DIMEN_UNSET || text == null) {
            cue
        } else {
            cue.buildUpon()
                .setLine(nextLine.toFloat(), Cue.LINE_TYPE_NUMBER)
                .build()
                .also { nextLine -= text.count { it == '\n' } + 1 }
        }
    }
}
