package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekBack
import dev.anilbeesetti.nextplayer.feature.player.extensions.seekForward

@UnstableApi
@Composable
fun rememberDoubleTapGestureHandler(
    player: Player,
    doubleTapGesture: DoubleTapGesture,
    seekIncrementMillis: Long,
    shouldFastSeek: (Long) -> Boolean,
): DoubleTapGestureHandler {
    val doubleTapGestureHandler = remember {
        DoubleTapGestureHandler(
            player = player,
            doubleTapGesture = doubleTapGesture,
            seekIncrementMillis = seekIncrementMillis,
            shouldFastSeek = shouldFastSeek,
        )
    }
    return doubleTapGestureHandler
}

@Stable
class DoubleTapGestureHandler(
    private val player: Player,
    private val doubleTapGesture: DoubleTapGesture,
    private val seekIncrementMillis: Long,
    private val shouldFastSeek: (Long) -> Boolean,
) {
    fun handleDoubleTap(offset: Offset, size: IntSize) {
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
                player.seekBack(
                    positionMs = player.currentPosition - seekIncrementMillis,
                    shouldFastSeek = shouldFastSeek(player.duration),
                )
            }

            DoubleTapAction.SEEK_FORWARD -> {
                player.seekForward(
                    positionMs = player.currentPosition + seekIncrementMillis,
                    shouldFastSeek = shouldFastSeek(player.duration),
                )
            }

            DoubleTapAction.PLAY_PAUSE -> {
                when (player.isPlaying) {
                    true -> player.pause()
                    false -> player.play()
                }
            }
        }
    }
}

enum class DoubleTapAction {
    SEEK_BACKWARD,
    SEEK_FORWARD,
    PLAY_PAUSE,
}