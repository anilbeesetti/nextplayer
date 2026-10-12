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
import androidx.media3.common.C
import androidx.media3.common.Player
import com.graviton.feature.player.extensions.setIsScrubbingModeEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberLongPressSeekState(player: Player): LongPressSeekState {
    val scope = rememberCoroutineScope()
    return remember(player) { LongPressSeekState(player, scope) }
}

/**
 * The hold-to-seek gesture.
 *
 * A hold starts on the left or right side of the video (see [HoldSeek.directionFor]) and moves the
 * player in that direction while the finger stays down: the first step is the configured seek
 * increment, and the seek rate ramps up from there ([HoldSeek]). The player is actually moved on
 * every tick, so the picture tracks the gesture instead of jumping once on release, and the values
 * exposed here are what the overlay draws: the signed [accumulatedMs] and the [targetPositionMs].
 *
 * The state deliberately keeps its last values after [stop], so the overlay's exit animation can
 * fade the final amount out instead of blanking instantly.
 */
@Stable
class LongPressSeekState(
    private val player: Player,
    private val scope: CoroutineScope,
    private val tickIntervalMs: Long = 120L,
) {
    var isActive: Boolean by mutableStateOf(false)
        private set

    /** -1 while rewinding, +1 while fast-forwarding, 0 before the first hold. */
    var direction: Int by mutableIntStateOf(0)
        private set

    /** Signed seek offset of the current hold, clamped to the media boundaries. */
    var accumulatedMs: Long by mutableLongStateOf(0L)
        private set

    /** Where the hold will leave the playhead. */
    var targetPositionMs: Long by mutableLongStateOf(0L)
        private set

    /** Acceleration step of the current hold; one haptic tick is fired per step. */
    var tier: Int by mutableIntStateOf(0)
        private set

    private var anchorPositionMs = 0L
    private var job: Job? = null

    /**
     * Starts a hold at [x] of a surface that is [width] pixels wide, seeking by [incrementMs] for
     * the first step. Returns false when the press cannot seek (not seekable, unknown position, or
     * a press in the middle of the video), in which case the caller must not treat it as a hold.
     */
    fun start(x: Float, width: Int, incrementMs: Long): Boolean {
        if (isActive) return true
        if (!player.isCurrentMediaItemSeekable) return false
        val position = player.currentPosition
        if (position == C.TIME_UNSET) return false

        val direction = HoldSeek.directionFor(x, width.toFloat()) ?: return false

        this.direction = direction
        anchorPositionMs = position.coerceAtLeast(0L)
        accumulatedMs = 0L
        targetPositionMs = anchorPositionMs
        tier = 0
        isActive = true
        player.setIsScrubbingModeEnabled(true)

        job?.cancel()
        job = scope.launch {
            val startedAt = System.nanoTime()
            var elapsedMs = 0L
            while (true) {
                applyElapsed(elapsedMs, incrementMs)
                delay(tickIntervalMs)
                elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            }
        }
        return true
    }

    /** Completes the hold: the player lands exactly on the target the user was shown. */
    fun stop() {
        if (!isActive) return

        job?.cancel()
        job = null
        isActive = false
        player.seekTo(targetPositionMs)
        player.setIsScrubbingModeEnabled(false)
    }

    /** Ends the hold without moving the player any further (media changed, surface gone). */
    fun cancel() {
        if (!isActive) return

        job?.cancel()
        job = null
        isActive = false
        player.setIsScrubbingModeEnabled(false)
    }

    private fun applyElapsed(elapsedMs: Long, incrementMs: Long) {
        val deltaMs = HoldSeek.deltaAt(elapsedMs, incrementMs)
        val target = clampedTarget(deltaMs)
        accumulatedMs = target - anchorPositionMs
        targetPositionMs = target
        tier = HoldSeek.tierAt(elapsedMs)
        player.seekTo(target)
    }

    private fun clampedTarget(deltaMs: Long): Long {
        val raw = anchorPositionMs + direction * deltaMs
        val duration = player.duration
        // An unknown duration (live, still resolving) only bounds the start of the media.
        val upperBound = if (duration > 0L) duration else Long.MAX_VALUE
        return raw.coerceIn(0L, upperBound)
    }
}
