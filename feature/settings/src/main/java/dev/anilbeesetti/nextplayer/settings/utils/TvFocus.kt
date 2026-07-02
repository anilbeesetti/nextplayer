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
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun rememberTvListFocusRequester(): FocusRequester = remember { FocusRequester() }

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

@Composable
fun Modifier.tvFocusDown(target: FocusRequester): Modifier {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    return if (isTv) this.focusProperties { down = target } else this
}
