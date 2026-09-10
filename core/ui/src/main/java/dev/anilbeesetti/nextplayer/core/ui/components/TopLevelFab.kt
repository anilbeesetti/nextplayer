package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector

object TopLevelFabKey {
    const val MEDIA = "media"
    const val PLAYLISTS = "playlists"
    const val NETWORK = "network"
    const val MORE = "more"
}

data class TopLevelFabState(
    val icon: ImageVector,
    val onClick: () -> Unit,
    val upFocusRequester: FocusRequester = FocusRequester.Default,
)

val LocalTopLevelFabSetter = compositionLocalOf<(String, TopLevelFabState?) -> Unit> { { _, _ -> } }
val LocalTopLevelBottomBarVisibleSetter = compositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
fun BindTopLevelBottomBarVisible(visible: Boolean) {
    val setter = LocalTopLevelBottomBarVisibleSetter.current

    DisposableEffect(visible, setter) {
        setter(visible)
        onDispose { setter(true) }
    }
}

@Composable
fun BindTopLevelFab(
    key: String,
    icon: ImageVector,
    upFocusRequester: FocusRequester = FocusRequester.Default,
    onClick: () -> Unit,
) {
    val setter = LocalTopLevelFabSetter.current
    val currentOnClick = rememberUpdatedState(onClick)

    DisposableEffect(key, icon, upFocusRequester, setter) {
        setter(
            key,
            TopLevelFabState(
                icon = icon,
                onClick = { currentOnClick.value() },
                upFocusRequester = upFocusRequester,
            ),
        )
        onDispose { setter(key, null) }
    }
}
