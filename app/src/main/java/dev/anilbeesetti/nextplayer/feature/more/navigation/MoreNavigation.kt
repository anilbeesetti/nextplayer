package dev.anilbeesetti.nextplayer.feature.more.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryScreen
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreScreen
import kotlinx.serialization.Serializable

@Serializable
object MoreRoute : NavKey

@Serializable
object HistoryRoute : NavKey

fun EntryProviderScope<NavKey>.moreEntry(
    onHistoryClick: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onVaultClick: () -> Unit,
) {
    entry<MoreRoute> {
        MoreScreen(
            onHistoryClick = onHistoryClick,
            onPlayVideo = onPlayVideo,
            onSettingsClick = onSettingsClick,
            onVaultClick = onVaultClick,
        )
    }
}

fun EntryProviderScope<NavKey>.historyEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (String) -> Unit,
) {
    entry<HistoryRoute> {
        HistoryScreen(
            onNavigateUp = onNavigateUp,
            onPlayVideo = onPlayVideo,
        )
    }
}

fun NavBackStack<NavKey>.navigateToMore() {
    add(MoreRoute)
}

fun NavBackStack<NavKey>.navigateToHistory() {
    add(HistoryRoute)
}
