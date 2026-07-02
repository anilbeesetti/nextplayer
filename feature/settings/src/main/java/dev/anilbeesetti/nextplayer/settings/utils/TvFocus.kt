package dev.anilbeesetti.nextplayer.settings.utils

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import kotlinx.coroutines.delay

/** Remembers a [FocusRequester] used to wire D-pad focus into a settings list on Android TV. */
@Composable
fun rememberTvListFocusRequester(): FocusRequester = remember { FocusRequester() }

/**
 * Makes a scrollable settings list reachable with a D-pad on Android TV.
 *
 * Settings screens lay their rows out in a `Column(verticalScroll(...))`. Directional focus can't
 * reliably enter such a column from the top app bar, so on a TV this turns the column into a
 * [focusGroup], tags it with [focusRequester] and moves focus onto its first focusable row when the
 * screen appears. Pair it with [tvFocusDown] on the top-bar back button so pressing down from there
 * also enters the list. On touch devices it's a no-op.
 */
@Composable
fun Modifier.tvListFocus(focusRequester: FocusRequester): Modifier {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    LaunchedEffect(isTv) {
        if (!isTv) return@LaunchedEffect
        // The column may still be laying out on the first frame; retry a few times.
        repeat(times = 5) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    return if (isTv) this.focusRequester(focusRequester).focusGroup() else this
}

/**
 * On Android TV, routes a downward D-pad press from this element (typically the top-bar back button)
 * to [target] — the [focusRequester] attached to a list via [tvListFocus] — so focus enters the
 * list instead of being lost at the app-bar boundary. No-op on touch devices.
 */
@Composable
fun Modifier.tvFocusDown(target: FocusRequester): Modifier {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    return if (isTv) this.focusProperties { down = target } else this
}
