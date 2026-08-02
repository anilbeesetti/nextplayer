package dev.anilbeesetti.nextplayer.feature.playlist.navigation

import android.net.Uri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.playlist.screens.detail.PlaylistDetailScreenRoute
import dev.anilbeesetti.nextplayer.feature.playlist.screens.detail.PlaylistDetailViewModel
import dev.anilbeesetti.nextplayer.feature.playlist.screens.list.PlaylistListScreenRoute
import dev.anilbeesetti.nextplayer.feature.playlist.screens.list.PlaylistListViewModel
import kotlinx.serialization.Serializable

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
        PlaylistListScreenRoute(
            viewModel = hiltViewModel<PlaylistListViewModel, PlaylistListViewModel.Factory>(
                creationCallback = { factory ->
                    factory.create(
                        PlaylistListViewModel.Output(
                            openPlaylist = onPlaylistClick,
                            openSettings = onSettingsClick,
                        ),
                    )
                },
            ),
        )
    }
}

fun EntryProviderScope<NavKey>.playlistDetailEntry(
    onNavigateUp: () -> Unit,
    onPlayVideos: (uris: List<Uri>, startUri: Uri) -> Unit,
) {
    entry<PlaylistDetailRoute> { route ->
        PlaylistDetailScreenRoute(
            onNavigateUp = onNavigateUp,
            onPlayVideos = onPlayVideos,
            viewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(route.playlistId) },
            ),
        )
    }
}
