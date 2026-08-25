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
import dev.anilbeesetti.nextplayer.core.ui.components.requestFocusUntilLanded
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing

@Composable
fun rememberTvListFocusRequester(): FocusRequester = remember { FocusRequester() }

@Composable
fun Modifier.tvListFocus(focusRequester: FocusRequester): Modifier {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    LaunchedEffect(isTv) {
        if (!isTv) return@LaunchedEffect
        focusRequester.requestFocusUntilLanded(attempts = 5)
    }

    return if (isTv) this.focusRequester(focusRequester).focusGroup() else this
}

@Composable
fun Modifier.tvFocusDown(target: FocusRequester): Modifier {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    // This is applied to the top-bar back button, so also give it a visible focus ring on TV.
    return if (isTv) this.tvFocusRing(isTv).focusProperties { down = target } else this
}
