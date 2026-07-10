package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun rememberTvListFocusRequester(): FocusRequester = remember { FocusRequester() }

/**
 * On Android TV, makes the decorated list a focus group and moves focus into it when it appears, so
 * D-pad users land on the content (the first item) rather than a top-bar action. No-op on touch.
 */
@Composable
fun Modifier.tvListFocus(focusRequester: FocusRequester): Modifier {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    LaunchedEffect(isTv) {
        if (!isTv) return@LaunchedEffect
        repeat(times = 5) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50.milliseconds)
        }
    }

    return if (isTv) this.focusRequester(focusRequester).focusGroup() else this
}
