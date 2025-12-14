package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.formatted
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekBack
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekForward
import dev.anilbeesetti.nextplayer.feature.player.extensions.setScrubbingModeEnabled
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
@Composable
fun rememberSeekGestureState(
    player: Player,
    shouldFastSeek: (Long) -> Boolean,
): SeekGestureState {
    val seekGestureState = remember {
        SeekGestureState(
            player = player,
            shouldFastSeek = shouldFastSeek,
        )
    }
    return seekGestureState
}

class SeekGestureState(
    private val player: Player,
    private val shouldFastSeek: (Long) -> Boolean,
) {
    var isSeeking: Boolean by mutableStateOf(false)
        private set

    var seekStartPosition: Long? by mutableStateOf(null)
        private set

    var seekAmount: Long? by mutableStateOf(null)
        private set

    private var seekStartX = 0f
    private var isPlayingOnDragStart: Boolean = false

    fun onDragStart(offset: Offset) {
        if (player.currentPosition == C.TIME_UNSET) return
        if (player.duration == C.TIME_UNSET) return

        isSeeking = true
        seekStartX = offset.x
        seekStartPosition = player.currentPosition
        isPlayingOnDragStart = player.isPlaying

        player.setScrubbingModeEnabled(true)
        player.pause()
    }

    fun onDrag(change: PointerInputChange, dragAmount: Float) {
        if (seekStartPosition == null) return
        if (player.duration == C.TIME_UNSET) return

        val newPosition = seekStartPosition!! + ((change.position.x - seekStartX) * 150f).toInt()
        seekAmount = (newPosition - seekStartPosition!!).coerceIn(
            minimumValue = 0 - seekStartPosition!!,
            maximumValue = player.duration - seekStartPosition!!,
        )

        if (dragAmount > 0) {
            player.seekForward(
                positionMs = newPosition.coerceAtMost(player.duration),
                shouldFastSeek = shouldFastSeek(player.duration),
            )
        } else {
            player.seekBack(
                positionMs = newPosition.coerceAtLeast(0L),
                shouldFastSeek = shouldFastSeek(player.duration),
            )
        }
    }

    fun onDragEnd() {
        player.setScrubbingModeEnabled(false)
        if (isPlayingOnDragStart) player.play()
        isSeeking = false
        seekStartPosition = null
        seekAmount = null

        seekStartX = 0f
        isPlayingOnDragStart = false
    }
}

val SeekGestureState.seekAmountFormatted: String
    get() {
        val seekAmount = seekAmount ?: return ""
        val sign = if (seekAmount < 0) "-" else "+"
        return sign + abs(seekAmount).milliseconds.formatted()
    }

val SeekGestureState.seekToPositionFormated: String
    get() {
        val position = seekStartPosition ?: return ""
        val seekAmount = seekAmount ?: return ""
        return (position + seekAmount).milliseconds.formatted()
    }