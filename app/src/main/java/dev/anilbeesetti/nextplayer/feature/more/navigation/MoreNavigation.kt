package dev.anilbeesetti.nextplayer.feature.more.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryRoute
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryViewModel
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreRoute
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreViewModel
import dev.anilbeesetti.nextplayer.feature.more.screens.trash.TrashRoute
import dev.anilbeesetti.nextplayer.feature.more.screens.trash.TrashViewModel
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
        MoreRoute(
            output = MoreViewModel.Output(
                openHistory = onHistoryClick,
                playVideo = onPlayVideo,
                openSettings = onSettingsClick,
                openTrash = onTrashClick,
                openVault = onVaultClick,
            ),
        )
    }
}

fun EntryProviderScope<NavKey>.historyEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (String) -> Unit,
) {
    entry<HistoryRoute> {
        HistoryRoute(
            output = HistoryViewModel.Output(
                navigateUp = onNavigateUp,
                playVideo = onPlayVideo,
            ),
        )
    }
}

fun EntryProviderScope<NavKey>.trashEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (String) -> Unit,
) {
    entry<TrashRoute> {
        TrashRoute(
            output = TrashViewModel.Output(
                navigateUp = onNavigateUp,
                playVideo = onPlayVideo,
            ),
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
