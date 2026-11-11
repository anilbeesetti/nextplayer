package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Keep cancellation separate from release: an interrupted gesture must never remove playback. */
@Composable
internal fun PlaybackSwipeToDismiss(
    onDismiss: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bothDirections: Boolean = false,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    var animation by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val allowed by rememberUpdatedState(enabled)
    val direction = LocalLayoutDirection.current
    val haptics = LocalHapticFeedback.current
    val targeted = enabled && width > 0 && abs(offset) >= width * .4f
    LaunchedEffect(targeted) {
        if (targeted) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    fun settle(remove: Boolean) {
        animation?.cancel()
        val committedDismiss = dismiss
        animation = scope.launch {
            val target = if (remove) width * offset.sign else 0f
            animate(
                initialValue = offset,
                targetValue = target,
                animationSpec = if (remove) tween(200) else spring(dampingRatio = .8f),
            ) { value, _ -> offset = value }
            if (remove && allowed) committedDismiss()
            offset = 0f
        }
    }
    val background by animateColorAsState(
        MaterialTheme.colorScheme.errorContainer.copy(alpha = if (targeted) 1f else .82f),
        label = "playback_remove_background",
    )
    val scale by animateFloatAsState(
        if (targeted) 1.08f else .95f,
        label = "playback_remove_icon",
    )
    Box(
        modifier.onSizeChanged {
            width = it.width
        }.pointerInput(
            enabled,
            bothDirections,
            direction,
        ) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = { animation?.cancel() },
                onHorizontalDrag = { change, amount ->
                    change.consume()
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
                },
                onDragCancel = { settle(false) },
                onDragEnd = { settle(allowed && width > 0 && abs(offset) >= width * .4f) },
            )
        },
    ) {
        if (offset != 0f) {
            Box(
                Modifier.matchParentSize().padding(horizontal = 8.dp).clip(shape)
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
