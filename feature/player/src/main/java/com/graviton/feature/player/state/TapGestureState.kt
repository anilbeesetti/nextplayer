package com.graviton.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.graviton.core.model.DoubleTapGesture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberTapGestureState(
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
            initialSeekIncrementMillis = seekIncrementMillis,
            initialUseLongPressGesture = useLongPressGesture,
            initialLongPressSpeed = longPressSpeed,
            coroutineScope = coroutineScope,
        )
    }
    tapGestureState.useLongPressGesture = useLongPressGesture
    tapGestureState.longPressSpeed = longPressSpeed
    // The increment is a live preference: changing it in settings applies to the next tap without
    // recreating the state (and so without losing an in-flight burst).
    tapGestureState.seekIncrementMillis = seekIncrementMillis
    return tapGestureState
}

@Stable
class TapGestureState(
    private val player: Player,
    initialSeekIncrementMillis: Long,
    initialUseLongPressGesture: Boolean = true,
    private val coroutineScope: CoroutineScope,
    initialLongPressSpeed: Float = 2.0f,
    val doubleTapGesture: DoubleTapGesture,
) : HoldSpeedController {
    var useLongPressGesture by mutableStateOf(initialUseLongPressGesture)
    var longPressSpeed by mutableStateOf(initialLongPressSpeed)
    var seekIncrementMillis by mutableLongStateOf(initialSeekIncrementMillis)

    /** Signed seek offset of the burst that is being shown, 0 when no burst is active. */
    var seekMillis by mutableLongStateOf(0L)
        private set

    /** Where the burst will leave the playhead; shown next to the offset. */
    var seekTargetPositionMs by mutableLongStateOf(0L)
        private set

    /** Where the last double tap landed, so the feedback can be drawn around the finger. */
    var lastDoubleTapOffset by mutableStateOf<Offset?>(null)
        private set

    /** Increments on every double tap; the feedback keys its ripple animation off it. */
    var doubleTapCount by mutableIntStateOf(0)
        private set

    var isLongPressGestureInAction by mutableStateOf(false)
    var activeLongPressSpeed by mutableStateOf(2.0f)

    private var resetJob: Job? = null
    private var holdStartX = 0f
    private var holdStartSpeed = 2.0f

    private val burst = DoubleTapBurst()
    private val speedBoost = TemporarySpeedBoost(applySpeed = { player.setPlaybackSpeed(it) })

    fun handleDoubleTap(offset: Offset, size: IntSize) {
        if (!player.isCurrentMediaItemSeekable) return

        val region = DoubleTapSeek.regionFor(offset.x, size.width.toFloat())
        val action = when (doubleTapGesture) {
            DoubleTapGesture.FAST_FORWARD_AND_REWIND -> when (region) {
                DoubleTapRegion.BACKWARD -> DoubleTapAction.SEEK_BACKWARD
                DoubleTapRegion.FORWARD -> DoubleTapAction.SEEK_FORWARD
                DoubleTapRegion.CENTER -> DoubleTapAction.NONE
            }

            DoubleTapGesture.BOTH -> when (region) {
                DoubleTapRegion.BACKWARD -> DoubleTapAction.SEEK_BACKWARD
                DoubleTapRegion.FORWARD -> DoubleTapAction.SEEK_FORWARD
                DoubleTapRegion.CENTER -> DoubleTapAction.PLAY_PAUSE
            }

            DoubleTapGesture.PLAY_PAUSE -> DoubleTapAction.PLAY_PAUSE

            DoubleTapGesture.NONE -> return
        }

        // The gesture is off (or the middle of the video is unbound): nothing is shown and nothing
        // is touched, so a disabled double tap cannot flash feedback either.
        if (action == DoubleTapAction.NONE) return

        lastDoubleTapOffset = offset
        doubleTapCount++

        when (action) {
            DoubleTapAction.SEEK_BACKWARD -> seekByBurst(direction = -1)
            DoubleTapAction.SEEK_FORWARD -> seekByBurst(direction = 1)
            DoubleTapAction.PLAY_PAUSE -> when (player.isPlaying) {
                true -> player.pause()
                false -> player.play()
            }
            DoubleTapAction.NONE -> Unit
        }
        resetDoubleTapSeekState()
    }

    /**
     * Adds one increment to the current burst and seeks to the accumulated target. The burst rules
     * (anchoring, re-anchoring on a turn, clamping) live in [DoubleTapBurst].
     */
    private fun seekByBurst(direction: Int) {
        val position = player.currentPosition.coerceAtLeast(0L)
        val target = burst.step(
            direction = direction,
            incrementMs = seekIncrementMillis,
            positionMs = position,
            durationMs = player.duration,
        )
        seekMillis = burst.offsetMs
        seekTargetPositionMs = target
        player.seekTo(target)
    }

    override val isHolding: Boolean get() = isLongPressGestureInAction

    fun handleLongPress(offset: Offset) {
        holdStartX = offset.x
        startHold()
    }

    /**
     * Begins the temporary speed boost.
     *
     * Returns `false` when the gesture is disabled or the player is not playing, in which case the
     * caller keeps its normal behaviour.
     */
    override fun startHold(): Boolean {
        if (!useLongPressGesture) return false
        if (!player.isPlaying) return false
        if (isLongPressGestureInAction) return true

        isLongPressGestureInAction = true
        holdStartSpeed = longPressSpeed
        activeLongPressSpeed = longPressSpeed
        // The speed the user selected is captured here, so a release restores it rather than 1x.
        speedBoost.start(currentSpeed = player.playbackParameters.speed, boostSpeed = longPressSpeed)
        return true
    }

    override fun endHold() = handleOnLongPressRelease()

    fun handleLongPressDrag(
        currentX: Float,
        screenWidth: Float,
        swipeThresholdPx: Float,
    ): Boolean {
        if (!isLongPressGestureInAction) return false

        val nextSpeed = HoldSpeedGesture.speedForSwipe(
            startSpeed = holdStartSpeed,
            deltaX = currentX - holdStartX,
            screenWidth = screenWidth,
            swipeThresholdPx = swipeThresholdPx,
        )
        if (nextSpeed == activeLongPressSpeed) return false

        activeLongPressSpeed = nextSpeed
        speedBoost.changeSpeed(nextSpeed)
        return true
    }

    fun handleOnLongPressRelease() {
        if (!isLongPressGestureInAction) return

        isLongPressGestureInAction = false
        speedBoost.stop()
    }

    private fun resetDoubleTapSeekState() {
        resetJob?.cancel()
        resetJob = coroutineScope.launch {
            delay(DoubleTapSeek.BURST_TIMEOUT_MS)
            burst.reset()
            seekMillis = 0L
        }
    }
}

enum class DoubleTapAction {
    SEEK_BACKWARD,
    SEEK_FORWARD,
    PLAY_PAUSE,

    /** A double tap in the middle of the video when that region is not bound to an action. */
    NONE,
}
