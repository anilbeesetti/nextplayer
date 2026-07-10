package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus repeatedly until it actually lands or [attempts] run out, returning whether it
 * landed.
 *
 * [requestFocus] can throw while the target isn't attached yet, and can also silently no-op while
 * the target is still being placed. Retrying on subsequent Compose frames keeps initial D-pad
 * focus reliable on Android TV without introducing visible 50ms gaps. When [isFocused] is provided
 * it is the source of truth; otherwise [FocusRequester.requestFocus]'s Boolean result is used.
 */
suspend fun FocusRequester.requestFocusUntilLanded(
    attempts: Int = 10,
    isFocused: (() -> Boolean)? = null,
): Boolean {
    repeat(attempts) {
        val requested = runCatching { requestFocus() }.getOrDefault(false)
        if (isFocused?.invoke() ?: requested) return true
        withFrameNanos { }
        if (isFocused?.invoke() == true) return true
    }
    return false
}
