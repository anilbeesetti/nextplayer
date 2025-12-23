package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import dev.anilbeesetti.nextplayer.feature.player.extensions.setIsScrubbingModeEnabled
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
@Composable
fun rememberSeekGestureState(player: Player): SeekGestureState {
    val seekGestureState = remember {
        SeekGestureState(player = player)
    }
    return seekGestureState
}

@Stable
class SeekGestureState(
    private val player: Player,
) {
    var isSeeking: Boolean by mutableStateOf(false)
        private set

    var seekStartPosition: Long? by mutableStateOf(null)
        private set

    var seekAmount: Long? by mutableStateOf(null)
        private set

    private var seekStartX = 0f

    fun onSeek(value: Long) {
        if (!isSeeking) {
            isSeeking = true
            seekStartPosition = player.currentPosition
            player.setIsScrubbingModeEnabled(true)
        }

        seekAmount = (value - seekStartPosition!!).coerceIn(
            minimumValue = 0 - seekStartPosition!!,
            maximumValue = player.duration - seekStartPosition!!,
        )

        if (value > player.currentPosition) {
            player.seekTo(value.coerceAtMost(player.duration))
        } else {
            player.seekTo(value.coerceAtLeast(0L))
        }
    }

    fun onSeekEnd() {
        reset()
    }

    fun onDragStart(offset: Offset) {
        if (player.currentPosition == C.TIME_UNSET) return
        if (player.duration == C.TIME_UNSET) return
        if (!player.isCurrentMediaItemSeekable) return

        isSeeking = true
        seekStartX = offset.x
        seekStartPosition = player.currentPosition

        player.setIsScrubbingModeEnabled(true)
    }

    @OptIn(UnstableApi::class)
    fun onDrag(change: PointerInputChange, dragAmount: Float) {
        if (seekStartPosition == null) return
        if (player.duration == C.TIME_UNSET) return
        if (!player.isCurrentMediaItemSeekable) return
        if (change.isConsumed) return

        val newPosition = seekStartPosition!! + ((change.position.x - seekStartX) * 150f).toInt()
        seekAmount = (newPosition - seekStartPosition!!).coerceIn(
            minimumValue = 0 - seekStartPosition!!,
            maximumValue = player.duration - seekStartPosition!!,
        )

        if (dragAmount > 0) {
            player.seekTo(newPosition.coerceAtMost(player.duration))
        } else {
            player.seekTo(newPosition.coerceAtLeast(0L))
        }
    }

    fun onDragEnd() {
        reset()
    }

    private fun reset() {
        player.setIsScrubbingModeEnabled(false)
        isSeeking = false
        seekStartPosition = null
        seekAmount = null

        seekStartX = 0f
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
