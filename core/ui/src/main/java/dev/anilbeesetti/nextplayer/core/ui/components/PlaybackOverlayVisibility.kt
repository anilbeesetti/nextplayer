package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

val LocalPlaybackOverlayHiddenSetter = compositionLocalOf<(Any, Boolean) -> Unit> { { _, _ -> } }

@Composable
fun BindPlaybackOverlayHidden(hidden: Boolean) {
    val owner = remember { Any() }
    val setter = LocalPlaybackOverlayHiddenSetter.current
    DisposableEffect(
        owner,
        setter,
        hidden,
    ) {
        setter(
            owner,
            hidden,
        )
        onDispose {
            setter(
                owner,
                false,
            )
        }
    }
}
