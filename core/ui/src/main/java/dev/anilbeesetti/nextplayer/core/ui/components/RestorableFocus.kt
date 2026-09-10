package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import kotlinx.coroutines.launch

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

/** Restore when content becomes available or the destination resumes, including return from playback. */
@Composable
fun Modifier.restorableFocusGroup(
    state: RestorableFocusState,
    ready: Boolean = true,
): Modifier {
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(state, ready) {
        val request = if (state.isTv && ready) {
            scope.launch {
                if (state.key == null || !state.itemRequester.requestFocusUntilLanded()) {
                    state.requester.requestFocusUntilLanded()
                }
            }
        } else {
            null
        }
        onPauseOrDispose { request?.cancel() }
    }
    if (!state.isTv) return this
    return focusRequester(state.requester)
        .focusProperties {
            onEnter = {
                if (state.key != null) runCatching { state.itemRequester.requestFocus() }
            }
        }
        .focusGroup()
}

@Composable
fun Modifier.restorableFocusItem(state: RestorableFocusState, key: String): Modifier {
    if (!state.isTv) return this
    // Comparing against the focused key directly would make every sibling observe it and recompose
    // on each focus move. Only the two items whose answer actually flips read a change here.
    val isRestoreTarget by remember(state, key) { derivedStateOf { key == state.key } }
    return thenIf(isRestoreTarget) { focusRequester(state.itemRequester) }
        .onFocusChanged { if (it.isFocused) state.key = key }
}
