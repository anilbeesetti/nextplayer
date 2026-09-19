package dev.anilbeesetti.nextplayer.feature.playlist.navigation

import android.net.Uri
import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.playlist.screens.detail.PlaylistDetailScreen
import dev.anilbeesetti.nextplayer.feature.playlist.screens.detail.PlaylistDetailViewModel
import dev.anilbeesetti.nextplayer.feature.playlist.screens.list.PlaylistListScreen
import dev.anilbeesetti.nextplayer.feature.playlist.screens.list.PlaylistListViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data object PlaylistListRoute : NavKey

@Serializable
data class PlaylistDetailRoute(val playlistId: Long) : NavKey

fun NavBackStack<NavKey>.navigateToPlaylistDetail(playlistId: Long) {
    add(PlaylistDetailRoute(playlistId))
}

fun EntryProviderScope<NavKey>.playlistListEntry(
    onPlaylistClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
) {
    entry<PlaylistListRoute> {
        val output = PlaylistListViewModel.Output(openPlaylist = onPlaylistClick, openSettings = onSettingsClick)
        val viewModel = koinViewModel<PlaylistListViewModel>(
            parameters = { parametersOf(output) },
        )
        SideEffect { viewModel.output = output }
        PlaylistListScreen(viewModel = viewModel)
    }
}

fun EntryProviderScope<NavKey>.playlistDetailEntry(
    onNavigateUp: () -> Unit,
    onPlayPlaylist: (playlistId: Long, startUri: Uri) -> Unit,
) {
    entry<PlaylistDetailRoute> { route ->
        val output = PlaylistDetailViewModel.Output(
            navigateUp = onNavigateUp,
            playPlaylist = onPlayPlaylist,
        )
        val viewModel = koinViewModel<PlaylistDetailViewModel>(
            parameters = {
                parametersOf(
                    PlaylistDetailViewModel.Input(playlistId = route.playlistId),
                    output,
                )
            },
        )
        SideEffect { viewModel.output = output }
        PlaylistDetailScreen(viewModel = viewModel)
    }
}
