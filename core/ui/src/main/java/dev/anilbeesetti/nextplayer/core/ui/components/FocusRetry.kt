package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * Requests focus repeatedly until it actually lands or [attempts] run out, returning whether it
 * landed.
 *
 * [requestFocus] can throw while the target isn't attached yet, and can also silently no-op while
 * the target is still being placed. Retrying past both cases makes initial D-pad focus reliable on
 * Android TV. When [isFocused] is provided it is the source of truth (a non-throwing call doesn't
 * guarantee focus moved); otherwise a call that doesn't throw is treated as success.
 */
suspend fun FocusRequester.requestFocusUntilLanded(
    attempts: Int = 10,
    delayMs: Long = 50,
    isFocused: (() -> Boolean)? = null,
): Boolean {
    repeat(attempts) {
        val requested = runCatching { requestFocus() }.isSuccess
        delay(delayMs)
        if (if (isFocused != null) isFocused() else requested) return true
    }
    return false
}
