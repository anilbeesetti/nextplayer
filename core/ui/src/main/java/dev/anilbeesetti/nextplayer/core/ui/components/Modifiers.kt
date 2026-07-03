package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.ui.Modifier

/**
 * Applies [block] only when [condition] is true, otherwise returns the receiver unchanged. Keeps
 * conditional modifier chains flat instead of nesting `then(if (...) ... else Modifier)`.
 */
inline fun Modifier.thenIf(condition: Boolean, block: Modifier.() -> Modifier): Modifier =
    if (condition) block() else this
