package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision

@Composable
fun rememberTvListFocusRequester(): FocusRequester = remember { FocusRequester() }

/**
 * Owns initial focus and child restoration for a TV content region. Keep this after scroll
 * modifiers so the region targets its interactive children, rather than the scroll container.
 * Pass a requester only when another region needs to enter this one. No-op on touch devices.
 */
@Composable
fun Modifier.tvListFocus(
    focusRequester: FocusRequester = rememberTvListFocusRequester(),
): Modifier {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    LaunchedEffect(isTv, focusRequester) {
        if (!isTv) return@LaunchedEffect
        focusRequester.requestFocusUntilLanded()
    }

    return if (isTv) {
        this.focusRequester(focusRequester)
            .focusRestorer()
            .focusGroup()
    } else {
        this
    }
}

@Composable
fun Modifier.tvFocusDown(target: FocusRequester): Modifier {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    return if (isTv) this.tvFocusRing(isTv).focusProperties { down = target } else this
}
