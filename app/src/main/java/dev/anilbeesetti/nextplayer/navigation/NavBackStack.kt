package dev.anilbeesetti.nextplayer.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** Repairs an empty restored stack before it is passed to NavDisplay. */
internal fun <T : NavKey> NavBackStack<T>.ensureRoot(root: T) {
    if (isEmpty()) add(root)
}

/** Pops a nested destination while preserving the stack's required root entry. */
internal fun <T : NavKey> NavBackStack<T>.removeLastIfNotRoot(): T? =
    if (size > 1) removeAt(lastIndex) else null
