package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where the portrait player stands: the full player, half queue under a banner, or the queue. */
internal enum class QueueStage { PLAYER, HALF, QUEUE }

/**
 * The top edge of the portrait queue panel, in pixels from the top of the player. It follows the
 * finger and settles on the nearest stage with a spring, keeping the release velocity. Readers
 * take [progress] in the layout and draw phases, so moving it never recomposes the player.
 */
@Stable
internal class PlaybackQueueSheet(private val scope: CoroutineScope) {
    private val top = Animatable(Float.MAX_VALUE)
    private var closed by mutableFloatStateOf(Float.MAX_VALUE)
    private var half by mutableFloatStateOf(Float.MAX_VALUE)
    private var full by mutableFloatStateOf(Float.MAX_VALUE)
    private var animation: Job? = null

    var stage by mutableStateOf(QueueStage.PLAYER)
        private set

    var settleSpec: SpringSpec<Float> = spring()

    /** Speed in pixels per second above which a release moves on to the next stage. */
    var flingThreshold = 1_000f

    val panelTop: Float get() = top.value

    /** 0 at the player, 1 at the half queue and 2 at the queue; a spring overshoots slightly. */
    val progress: Float
        get() {
            val value = top.value
            if (closed <= half || half <= full) return 0f
            return if (value >= half) {
                (closed - value) / (closed - half)
            } else {
                1f + (half - value) / (half - full)
            }
        }

    /** True while the panel shows, including while it moves between stages. */
    val engaged: Boolean get() = top.value < closed - 0.5f

    val atRest: Boolean get() = !top.isRunning && top.value in listOf(
        full,
        half,
        closed,
    )

    fun setAnchors(closed: Float, half: Float, full: Float) {
        if (closed == this.closed && half == this.half && full == this.full) return
        this.closed = closed
        this.half = half
        this.full = full
        if (!top.isRunning) launch { top.snapTo(anchor(stage)) }
    }

    /** Moves the panel with the finger and returns the distance it actually moved. */
    fun dragBy(delta: Float): Float {
        val from = top.value
        val to = (from + delta).coerceIn(
            full,
            closed,
        )
        if (to != from) launch { top.snapTo(to) }
        return to - from
    }

    /** Moves the panel up, but never past the stage it rests at; only the handle opens further. */
    fun raiseBy(delta: Float): Float {
        val from = top.value
        val to = (from + delta).coerceIn(
            minOf(
                anchor(stage),
                from,
            ),
            closed,
        )
        if (to != from) launch { top.snapTo(to) }
        return to - from
    }

    fun settle(velocity: Float) {
        val position = top.value
        val anchors = listOf(
            full,
            half,
            closed,
        )
        val target = when {
            velocity < -flingThreshold -> anchors.lastOrNull { it < position - 1f } ?: full
            velocity > flingThreshold -> anchors.firstOrNull { it > position + 1f } ?: closed
            else -> anchors.minBy { abs(it - position) }
        }
        animateTo(
            when (target) {
                full -> QueueStage.QUEUE
                half -> QueueStage.HALF
                else -> QueueStage.PLAYER
            },
            velocity,
        )
    }

    fun animateTo(target: QueueStage, velocity: Float = 0f, then: () -> Unit = {}) {
        stage = target
        launch {
            top.animateTo(
                anchor(target),
                settleSpec,
                initialVelocity = velocity,
            )
            then()
        }
    }

    /** One stage towards the player, as Back does. */
    fun stepBack() = animateTo(
        if (stage == QueueStage.QUEUE) QueueStage.HALF else QueueStage.PLAYER,
    )

    fun snapTo(target: QueueStage) {
        stage = target
        launch { top.snapTo(anchor(target)) }
    }

    private fun anchor(stage: QueueStage) = when (stage) {
        QueueStage.PLAYER -> closed
        QueueStage.HALF -> half
        QueueStage.QUEUE -> full
    }

    private fun launch(block: suspend () -> Unit) {
        animation?.cancel()
        animation = scope.launch(start = CoroutineStart.UNDISPATCHED) { block() }
    }
}
