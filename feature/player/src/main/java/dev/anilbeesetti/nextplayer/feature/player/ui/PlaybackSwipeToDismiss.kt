package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

private const val DISMISS_THRESHOLD = .4f

/** Keep cancellation separate from release: an interrupted gesture must never remove playback. */
@Composable
internal fun PlaybackSwipeToDismiss(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bothDirections: Boolean = false,
    content: @Composable () -> Unit,
) {
    var cancelled by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * DISMISS_THRESHOLD },
    )
    val dismiss by rememberUpdatedState(onDismiss)
    val allowed by rememberUpdatedState(enabled)
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val targeted by remember {
        derivedStateOf {
            allowed && width > 0 && abs(state.requireOffset()) >= width * DISMISS_THRESHOLD
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { targeted }.drop(1).collect { reached ->
            when {
                state.settledValue != SwipeToDismissBoxValue.Settled -> Unit

                reached -> haptics.performHapticFeedback(
                    HapticFeedbackType.GestureThresholdActivate,
                )

                else -> ViewCompat.performHapticFeedback(
                    view,
                    HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE,
                )
            }
        }
    }
    LaunchedEffect(state.settledValue) {
        if (state.settledValue != SwipeToDismissBoxValue.Settled) {
            if (!cancelled) delay(1_000)
            state.reset()
        }
    }
    val onDismissed = remember {
        { _: SwipeToDismissBoxValue ->
            if (!cancelled) {
                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                if (allowed) dismiss()
            }
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier.onSizeChanged { width = it.width }.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                cancelled = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    // A released pointer is only consumed this early when the system cancelled it.
                    if (event.changes.any { it.changedToUpIgnoreConsumed() && it.isConsumed }) {
                        cancelled = true
                    }
                } while (event.changes.any { it.pressed })
            }
        },
        enableDismissFromStartToEnd = bothDirections,
        gesturesEnabled = enabled,
        onDismiss = onDismissed,
        backgroundContent = {
            if (!bothDirections && state.dismissDirection != SwipeToDismissBoxValue.Settled) {
                val background by animateColorAsState(
                    MaterialTheme.colorScheme.errorContainer.copy(
                        alpha = if (targeted) 1f else .82f,
                    ),
                    animationSpec = tween(150),
                    label = "playback_remove_background",
                )
                val scale by animateFloatAsState(
                    if (targeted) 1.08f else .95f,
                    animationSpec = tween(120),
                    label = "playback_remove_icon",
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .fillMaxHeight()
                        .layout { measurable, constraints ->
                            val revealed = abs(state.requireOffset()).roundToInt()
                            val placeable = measurable.measure(
                                Constraints.fixed(
                                    revealed,
                                    constraints.maxHeight,
                                ),
                            )
                            layout(
                                revealed,
                                constraints.maxHeight,
                            ) {
                                placeable.place(
                                    0,
                                    0,
                                )
                            }
                        }
                        .clip(CircleShape)
                        .background(background)
                        .testTag("playback_dismiss_reveal")
                        .semantics { hideFromAccessibility() },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        null,
                        Modifier.padding(horizontal = 16.dp).size(24.dp)
                            .testTag("playback_dismiss_icon").graphicsLayer {
                                alpha = (abs(state.requireOffset()) / 56.dp.toPx()).coerceIn(
                                    0f,
                                    1f,
                                ) * if (targeted) 1f else .88f
                                scaleX = scale
                                scaleY = scale
                            },
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
    ) { content() }
}
