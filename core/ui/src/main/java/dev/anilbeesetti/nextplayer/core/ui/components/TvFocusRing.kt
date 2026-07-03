package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision

/**
 * Draws a ring around a component while it (or a child) holds D-pad focus, so the focus state is
 * clearly visible on Android TV. No-op on touch devices, where [isTv] is `false`.
 *
 * Pass a [shape] that matches the component's own outline (default [CircleShape] for icon buttons).
 */
@Composable
fun Modifier.tvFocusRing(
    isTv: Boolean,
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 3.dp,
): Modifier {
    if (!isTv) return this
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.hasFocus }
        .thenIf(focused) { border(width = width, color = color, shape = shape) }
}

/**
 * Convenience overload of [tvFocusRing] that detects Android TV itself, for call sites that don't
 * already have an `isTv` flag in scope.
 */
@Composable
fun Modifier.tvFocusRing(
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 3.dp,
): Modifier {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    return tvFocusRing(isTv = isTv, shape = shape, color = color, width = width)
}
