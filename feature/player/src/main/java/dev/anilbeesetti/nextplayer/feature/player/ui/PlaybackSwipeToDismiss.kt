package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DISMISS_THRESHOLD = .4f

private enum class SwipePhase { RESISTED, SNAPPING, FREE }

/**
 * The drag resists at first, then springs to the finger. SwipeToDismissBox is not used because it
 * has no resistance and hard-codes fling-to-dismiss and its settle animations.
 *
 * Keep cancellation separate from release: an interrupted gesture must never remove playback.
 */
@Composable
internal fun PlaybackSwipeToDismiss(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bothDirections: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val resistedDistance = with(density) { (if (bothDirections) 100.dp else 60.dp).toPx() }
    val resistedTravel = with(density) { (if (bothDirections) 30.dp else 20.dp).toPx() }
    val offset = remember { Animatable(0f) }
    var dragged by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf(SwipePhase.RESISTED) }
    var released by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    var width by remember { mutableIntStateOf(0) }
    var animation by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val allowed by rememberUpdatedState(enabled)
    val direction = LocalLayoutDirection.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val targeted = enabled && width > 0 && abs(dragged) >= width * DISMISS_THRESHOLD

    fun animate(block: suspend () -> Unit) {
        animation?.cancel()
        animation = scope.launch(start = CoroutineStart.UNDISPATCHED) { block() }
    }

    fun settle(remove: Boolean) {
        val committedDismiss = dismiss
        released = remove
        if (remove && !bothDirections) haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
        val exit = width * dragged.sign
        if (!remove) dragged = 0f
        animate {
            if (remove) {
                offset.animateTo(
                    exit,
                    tween(
                        if (bothDirections) 200 else 180,
                        easing = FastOutSlowInEasing,
                    ),
                )
                if (allowed) committedDismiss()
                delay(1_000)
            }
            offset.animateTo(
                0f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
            dragged = 0f
            released = false
        }
    }

    val background by animateColorAsState(
        MaterialTheme.colorScheme.errorContainer.copy(alpha = if (targeted) 1f else .82f),
        animationSpec = tween(150),
        label = "playback_remove_background",
    )
    val scale by animateFloatAsState(
        if (targeted) 1.08f else .95f,
        animationSpec = tween(120),
        label = "playback_remove_icon",
    )
    val removeSide = if (direction == LayoutDirection.Rtl) 1f else -1f
    Box(
        modifier.onSizeChanged {
            width = it.width
            height = it.height
        }.pointerInput(
            enabled,
            bothDirections,
            direction,
        ) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = {
                    if (!released) {
                        animation?.cancel()
                        dragged = 0f
                        phase = SwipePhase.RESISTED
                    }
                },
                onHorizontalDrag = { change, amount ->
                    if (released) return@detectHorizontalDragGestures
                    change.consume()
                    val wasTargeted = width > 0 && abs(dragged) >= width * DISMISS_THRESHOLD
                    dragged = (dragged + amount).let {
                        when {
                            bothDirections -> it.coerceIn(
                                -width.toFloat(),
                                width.toFloat(),
                            )

                            removeSide > 0f -> it.coerceIn(
                                0f,
                                width.toFloat(),
                            )

                            else -> it.coerceIn(
                                -width.toFloat(),
                                0f,
                            )
                        }
                    }
                    val nowTargeted = width > 0 && abs(dragged) >= width * DISMISS_THRESHOLD
                    if (!bothDirections && nowTargeted != wasTargeted) {
                        if (nowTargeted) {
                            haptics.performHapticFeedback(
                                HapticFeedbackType.GestureThresholdActivate,
                            )
                        } else {
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE,
                            )
                        }
                    }
                    when (phase) {
                        SwipePhase.RESISTED -> if (abs(dragged) < resistedDistance) {
                            val target = dragged / resistedDistance * resistedTravel
                            animate { offset.snapTo(target) }
                        } else {
                            phase = SwipePhase.SNAPPING
                        }

                        SwipePhase.SNAPPING -> {
                            haptics.performHapticFeedback(
                                if (bothDirections) {
                                    HapticFeedbackType.LongPress
                                } else {
                                    HapticFeedbackType.GestureThresholdActivate
                                },
                            )
                            val target = dragged
                            animate {
                                offset.animateTo(
                                    target,
                                    spring(
                                        dampingRatio = .8f,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                )
                            }
                            phase = SwipePhase.FREE
                        }

                        SwipePhase.FREE -> {
                            val target = dragged
                            animate {
                                offset.animateTo(
                                    target,
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessHigh,
                                    ),
                                )
                            }
                        }
                    }
                },
                onDragCancel = { if (!released) settle(false) },
                onDragEnd = {
                    if (!released) {
                        settle(allowed && width > 0 && abs(dragged) >= width * DISMISS_THRESHOLD)
                    }
                },
            )
        },
    ) {
        // The return spring overshoots past zero; nothing is being removed on that side.
        if (!bothDirections && offset.value * removeSide > 0f) {
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .width(with(density) { abs(offset.value).toDp() })
                    .height(with(density) { height.toDp() })
                    .clip(CircleShape)
                    .background(background).testTag("playback_dismiss_reveal")
                    .semantics { hideFromAccessibility() },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    null,
                    Modifier.padding(
                        horizontal = 16.dp,
                    ).size(24.dp).testTag("playback_dismiss_icon").graphicsLayer {
                        alpha = (abs(offset.value) / with(density) { 56.dp.toPx() }).coerceIn(
                            0f,
                            1f,
                        ) *
                            if (targeted) 1f else .88f
                        scaleX = scale
                        scaleY = scale
                    },
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Box(Modifier.graphicsLayer { translationX = offset.value }) { content() }
    }
}
