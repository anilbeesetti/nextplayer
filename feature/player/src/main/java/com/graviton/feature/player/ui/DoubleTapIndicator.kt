package com.graviton.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.graviton.feature.player.state.TapGestureState
import kotlinx.coroutines.delay
import kotlin.math.max

/** How long the amount stays on screen after the last tap of a burst. */
private const val INDICATOR_DWELL_MS = 1_300L

/**
 * The seek amount and the ripple that a burst of double taps leaves behind.
 *
 * The feedback is anchored to where the user actually tapped — the ripple grows from that point and
 * the card appears on the matching side of the screen, away from the subtitles — instead of sliding
 * the whole picture sideways the way the old implementation did.
 *
 * The values shown are captured while the burst is live and kept afterwards, so the exit animation
 * still has something to fade out. The card outlives the burst window on purpose
 * ([INDICATOR_DWELL_MS] after the last tap), so the final amount can actually be read; a further
 * tap restarts the dwell instead of flickering the card.
 */
@Composable
fun DoubleTapIndicator(modifier: Modifier = Modifier, tapGestureState: TapGestureState) {
    var deltaMs by remember { mutableStateOf(0L) }
    var targetPositionMs by remember { mutableStateOf(0L) }
    var tapOffset by remember { mutableStateOf<Offset?>(null) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(tapGestureState.doubleTapCount) {
        val count = tapGestureState.doubleTapCount
        if (count == 0) return@LaunchedEffect
        val seekMillis = tapGestureState.seekMillis
        // A burst that was clamped to nothing (already at the edge) has no amount to show.
        if (seekMillis == 0L) return@LaunchedEffect

        deltaMs = seekMillis
        targetPositionMs = tapGestureState.seekTargetPositionMs
        tapOffset = tapGestureState.lastDoubleTapOffset
        isVisible = true

        delay(INDICATOR_DWELL_MS)
        // Only the tap that started this effect may hide the card; a newer one owns it now.
        if (tapGestureState.doubleTapCount == count) isVisible = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        DoubleTapRipple(
            center = tapOffset,
            rippleKey = tapGestureState.doubleTapCount,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = isVisible,
            modifier = Modifier.align(
                if (deltaMs > 0) Alignment.CenterEnd else Alignment.CenterStart,
            ),
            enter = fadeIn(animationSpec = tween(120)) + scaleIn(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                initialScale = 0.92f,
            ),
            exit = fadeOut(animationSpec = tween(220)) + scaleOut(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                targetScale = 0.96f,
            ),
        ) {
            SeekFeedbackCard(
                deltaMs = deltaMs,
                targetPositionMs = targetPositionMs,
                // Inset from the edge and lifted off the middle, so the card stays clear of the
                // screen edges, the system gesture areas and the subtitle band.
                modifier = Modifier
                    // Clear of display cutouts and the system bars, then lifted further so the
                    // card never lands on the control strips or the subtitle band.
                    .safeDrawingPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 64.dp),
            )
        }
    }
}

/**
 * A radial highlight around the tapped point, restarting on every tap in the burst. It is drawn,
 * not composed per tap, so a rapid burst costs one small canvas.
 */
@Composable
private fun DoubleTapRipple(
    center: Offset?,
    rippleKey: Int,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(rippleKey) {
        if (rippleKey == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = LinearOutSlowInEasing),
        )
    }

    if (center == null) return
    val rippleColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val current = progress.value
        if (current >= 1f) return@Canvas

        val radius = max(28.dp.toPx(), 40.dp.toPx() + 120.dp.toPx() * current)
        val fade = (1f - current).coerceIn(0f, 1f)

        drawCircle(
            color = rippleColor.copy(alpha = 0.22f * fade),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.30f * fade),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
