package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberTapGesureState(
    player: Player,
    doubleTapGesture: DoubleTapGesture,
    seekIncrementMillis: Long,
    useLongPressGesture: Boolean,
    longPressSpeed: Float,
): TapGestureState {
    val coroutineScope = rememberCoroutineScope()
    val tapGestureState = remember {
        TapGestureState(
            player = player,
            doubleTapGesture = doubleTapGesture,
            seekIncrementMillis = seekIncrementMillis,
            useLongPressGesture = useLongPressGesture,
            longPressSpeed = longPressSpeed,
            coroutineScope = coroutineScope,
        )
    }
    return tapGestureState
}

@Stable
class TapGestureState(
    private val player: Player,
    private val seekIncrementMillis: Long,
    private val useLongPressGesture: Boolean = true,
    private val coroutineScope: CoroutineScope,
    val longPressSpeed: Float = 2.0f,
    val doubleTapGesture: DoubleTapGesture,
    val interactionSource: MutableInteractionSource = MutableInteractionSource(),
) {
    var seekMillis by mutableLongStateOf(0L)
    var isLongPressGestureInAction by mutableStateOf(false)

    private var resetJob: Job? = null
    private var currentSpeed: Float = player.playbackParameters.speed

    fun handleDoubleTap(offset: Offset, size: IntSize) {
        if (!player.isCurrentMediaItemSeekable) return

        val action = when (doubleTapGesture) {
            DoubleTapGesture.FAST_FORWARD_AND_REWIND -> {
                val viewCenterX = size.width / 2
                when {
                    offset.x < viewCenterX -> DoubleTapAction.SEEK_BACKWARD
                    else -> DoubleTapAction.SEEK_FORWARD
                }
            }

            DoubleTapGesture.BOTH -> {
                val eventPositionX = offset.x / size.width
                when {
                    eventPositionX < 0.35 -> DoubleTapAction.SEEK_BACKWARD
                    eventPositionX > 0.65 -> DoubleTapAction.SEEK_FORWARD
                    else -> DoubleTapAction.PLAY_PAUSE
                }
            }

            DoubleTapGesture.PLAY_PAUSE -> DoubleTapAction.PLAY_PAUSE

            DoubleTapGesture.NONE -> return
        }

        when (action) {
            DoubleTapAction.SEEK_BACKWARD -> {
                player.seekTo(player.currentPosition - seekIncrementMillis)
                if (seekMillis > 0L) {
                    seekMillis = 0L
                }
                seekMillis -= seekIncrementMillis
                interactionSource.tryEmit(PressInteraction.Press(offset))
            }

            DoubleTapAction.SEEK_FORWARD -> {
                player.seekTo(player.currentPosition + seekIncrementMillis)
                if (seekMillis < 0L) {
                    seekMillis = 0L
                }
                seekMillis += seekIncrementMillis
                interactionSource.tryEmit(PressInteraction.Press(offset))
            }

            DoubleTapAction.PLAY_PAUSE -> {
                when (player.isPlaying) {
                    true -> player.pause()
                    false -> player.play()
                }
            }
        }
        resetDoubleTapSeekState()
    }

    fun handleOnLongPressRelease() {
        if (isLongPressGestureInAction) {
            isLongPressGestureInAction = false
            player.setPlaybackSpeed(currentSpeed)
        }
    }

    fun handleLongPress(offset: Offset) {
        if (!useLongPressGesture) return
        if (!player.isPlaying) return

        isLongPressGestureInAction = true
        currentSpeed = player.playbackParameters.speed
        player.setPlaybackSpeed(longPressSpeed)
    }

    private fun resetDoubleTapSeekState() {
        resetJob?.cancel()
        resetJob = coroutineScope.launch {
            delay(750.milliseconds)
            seekMillis = 0L
        }
    }
}

enum class DoubleTapAction {
    SEEK_BACKWARD,
    SEEK_FORWARD,
    PLAY_PAUSE,
}
