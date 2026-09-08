package dev.anilbeesetti.nextplayer.feature.more.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryScreen
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreScreen
import dev.anilbeesetti.nextplayer.feature.more.screens.trash.TrashScreen
import kotlinx.serialization.Serializable

@Serializable
object MoreRoute : NavKey

@Serializable
object HistoryRoute : NavKey

@Serializable
object TrashRoute : NavKey

fun EntryProviderScope<NavKey>.moreEntry(
    onHistoryClick: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onTrashClick: () -> Unit,
    onVaultClick: () -> Unit,
) {
    entry<MoreRoute> {
        MoreScreen(
            onHistoryClick = onHistoryClick,
            onPlayVideo = onPlayVideo,
            onSettingsClick = onSettingsClick,
            onTrashClick = onTrashClick,
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

fun EntryProviderScope<NavKey>.trashEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (String) -> Unit,
) {
    entry<TrashRoute> {
        TrashScreen(
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

fun NavBackStack<NavKey>.navigateToTrash() {
    add(TrashRoute)
}
