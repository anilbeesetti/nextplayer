package dev.anilbeesetti.nextplayer.feature.more.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreScreen
import kotlinx.serialization.Serializable

@Serializable
object MoreRoute : NavKey

fun EntryProviderScope<NavKey>.moreEntry(
    onPlayVideo: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onVaultClick: () -> Unit,
) {
    entry<MoreRoute> {
        MoreScreen(
            onPlayVideo = onPlayVideo,
            onSettingsClick = onSettingsClick,
            onVaultClick = onVaultClick,
        )
    }
}

fun NavBackStack<NavKey>.navigateToMore() {
    add(MoreRoute)
}
