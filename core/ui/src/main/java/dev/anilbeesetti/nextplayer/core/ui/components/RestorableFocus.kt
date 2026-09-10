package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision

/** Keeps item identity across destination recreation and when entering from another focus region. */
@Stable
class RestorableFocusState internal constructor(
    internal val isTv: Boolean,
    private val focusedKey: MutableState<String?>,
) {
    val requester = FocusRequester()
    internal val itemRequester = FocusRequester()

    internal var key: String?
        get() = focusedKey.value
        set(value) {
            focusedKey.value = value
        }
}

@Composable
fun rememberRestorableFocusState(): RestorableFocusState {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val focusedKey = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(isTv) { RestorableFocusState(isTv, focusedKey) }
}

/** Request focus once content is available. Later data updates must not steal focus from a toolbar. */
@Composable
fun Modifier.restorableFocusGroup(
    state: RestorableFocusState,
    ready: Boolean = true,
): Modifier {
    if (!state.isTv) return this
    var restoredInitialFocus by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state, ready) {
        if (!ready || restoredInitialFocus) return@LaunchedEffect
        restoredInitialFocus = (state.key != null && state.itemRequester.requestFocusUntilLanded()) ||
            state.requester.requestFocusUntilLanded()
    }
    return focusRequester(state.requester)
        .focusProperties {
            onEnter = {
                if (state.key != null) runCatching { state.itemRequester.requestFocus() }
            }
        }
        .focusGroup()
}

fun Modifier.restorableFocusItem(state: RestorableFocusState, key: String): Modifier {
    if (!state.isTv) return this
    return thenIf(key == state.key) { focusRequester(state.itemRequester) }
        .onFocusChanged { if (it.isFocused) state.key = key }
}
