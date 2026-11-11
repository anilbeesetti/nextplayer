package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.AbsoluteAlignment
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keep cancellation separate from release: an interrupted gesture must never remove playback.
 * SwipeToDismissBox is not used because it hard-codes fling-to-dismiss and its settle animations.
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
    var released by remember { mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    var width by remember { mutableIntStateOf(0) }
    var animation by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val allowed by rememberUpdatedState(enabled)
    val direction = LocalLayoutDirection.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val targeted = enabled && width > 0 && abs(offset) >= width * .4f
    fun settle(remove: Boolean) {
        animation?.cancel()
        val committedDismiss = dismiss
        released = remove
        if (remove) haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
        animation = scope.launch {
            val target = if (remove) width * offset.sign else 0f
            animate(
                initialValue = offset,
                targetValue = target,
                animationSpec = if (remove) {
                    tween(
                        if (bothDirections) 200 else 180,
                        easing = FastOutSlowInEasing,
                    )
                } else {
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                },
            ) { value, _ -> offset = value }
            if (remove && allowed) committedDismiss()
            if (remove) kotlinx.coroutines.delay(1_000)
            animate(
                offset,
                0f,
                animationSpec = spring(),
            ) { value, _ -> offset = value }
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
                    if (!released) animation?.cancel()
                },
                onHorizontalDrag = { change, amount ->
                    if (released) return@detectHorizontalDragGestures
                    change.consume()
                    val wasTargeted = width > 0 && abs(offset) >= width * .4f
                    offset = (offset + amount).let {
                        when {
                            bothDirections -> it.coerceIn(
                                -width.toFloat(),
                                width.toFloat(),
                            )

                            direction == LayoutDirection.Rtl -> it.coerceIn(
                                0f,
                                width.toFloat(),
                            )

                            else -> it.coerceIn(
                                -width.toFloat(),
                                0f,
                            )
                        }
                    }
                    val nowTargeted = width > 0 && abs(offset) >= width * .4f
                    if (nowTargeted != wasTargeted) {
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
                },
                onDragCancel = { if (!released) settle(false) },
                onDragEnd = {
                    if (!released) settle(allowed && width > 0 && abs(offset) >= width * .4f)
                },
            )
        },
    ) {
        if (offset != 0f && !bothDirections) {
            Box(
                Modifier.align(
                    if (offset >
                        0f
                    ) {
                        AbsoluteAlignment.CenterLeft
                    } else {
                        AbsoluteAlignment.CenterRight
                    },
                )
                    .width(with(density) { abs(offset).toDp() })
                    .height(with(density) { height.toDp() })
                    .clip(CircleShape)
                    .background(background).testTag("playback_dismiss_reveal")
                    .semantics { hideFromAccessibility() },
                contentAlignment = if (offset >
                    0f
                ) {
                    AbsoluteAlignment.CenterLeft
                } else {
                    AbsoluteAlignment.CenterRight
                },
            ) {
                Icon(
                    Icons.Rounded.Close,
                    null,
                    Modifier.padding(
                        horizontal = 16.dp,
                    ).size(24.dp).testTag("playback_dismiss_icon").graphicsLayer {
                        alpha = (abs(offset) / with(density) { 56.dp.toPx() }).coerceIn(
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
        Box(Modifier.graphicsLayer { translationX = offset }) { content() }
    }
}
