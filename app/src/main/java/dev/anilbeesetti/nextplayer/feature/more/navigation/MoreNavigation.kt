package dev.anilbeesetti.nextplayer.feature.more.navigation

import androidx.compose.runtime.SideEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryScreen
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryViewModel
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreScreen
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreViewModel
import dev.anilbeesetti.nextplayer.feature.more.screens.trash.TrashScreen
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
        val output = MoreViewModel.Output(
            openHistory = onHistoryClick,
            playVideo = onPlayVideo,
            openSettings = onSettingsClick,
            openTrash = onTrashClick,
            openVault = onVaultClick,
        )
        val viewModel = hiltViewModel<MoreViewModel, MoreViewModel.Factory>(
            creationCallback = { factory -> factory.create(output = output) },
        )
        SideEffect { viewModel.output = output }
        MoreScreen(viewModel = viewModel)
    }
}

fun EntryProviderScope<NavKey>.historyEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (String) -> Unit,
) {
    entry<HistoryRoute> {
        val output = HistoryViewModel.Output(
            navigateUp = onNavigateUp,
            playVideo = onPlayVideo,
        )
        val viewModel = hiltViewModel<HistoryViewModel, HistoryViewModel.Factory>(
            creationCallback = { factory -> factory.create(output = output) },
        )
        SideEffect { viewModel.output = output }
        HistoryScreen(viewModel = viewModel)
    }
}

fun EntryProviderScope<NavKey>.trashEntry(
    onNavigateUp: () -> Unit,
    onPlayVideo: (String) -> Unit,
) {
    entry<TrashRoute> {
        val output = TrashViewModel.Output(
            navigateUp = onNavigateUp,
            playVideo = onPlayVideo,
        )
        val viewModel = hiltViewModel<TrashViewModel, TrashViewModel.Factory>(
            creationCallback = { factory -> factory.create(output = output) },
        )
        SideEffect { viewModel.output = output }
        TrashScreen(viewModel = viewModel)
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
