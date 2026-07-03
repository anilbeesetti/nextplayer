package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Wires an item into D-pad focus restoration on Android TV.
 *
 * Every item reports itself through [onFocused] when it gains focus, so the caller can remember the
 * last focused [key] in saveable state. The item whose [key] equals [restoredKey] is given
 * [restoreRequester] so it can be focused when the screen is re-entered (e.g. after navigating back
 * from a detail screen) instead of always jumping to the first item. No-op on touch devices.
 */
fun Modifier.restorableFocusItem(
    isTv: Boolean,
    key: String,
    restoredKey: String?,
    restoreRequester: FocusRequester,
    onFocused: (String) -> Unit,
): Modifier {
    if (!isTv) return this
    return this
        .thenIf(key == restoredKey) { focusRequester(restoreRequester) }
        .onFocusChanged { if (it.isFocused) onFocused(key) }
}
