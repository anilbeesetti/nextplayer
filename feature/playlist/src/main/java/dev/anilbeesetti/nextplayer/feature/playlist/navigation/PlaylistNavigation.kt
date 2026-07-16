package dev.anilbeesetti.nextplayer.feature.playlist.navigation

import android.net.Uri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.playlist.screens.detail.PlaylistDetailScreenRoute
import dev.anilbeesetti.nextplayer.feature.playlist.screens.detail.PlaylistDetailViewModel
import dev.anilbeesetti.nextplayer.feature.playlist.screens.list.PlaylistListScreenRoute
import kotlinx.serialization.Serializable

@Serializable
data object PlaylistListRoute : NavKey

@Serializable
data class PlaylistDetailRoute(val playlistId: Long) : NavKey

fun NavBackStack<NavKey>.navigateToPlaylistDetail(playlistId: Long) {
    add(PlaylistDetailRoute(playlistId))
}

fun EntryProviderScope<NavKey>.playlistListEntry(
    onPlaylistClick: (playlistId: Long) -> Unit,
    onSettingsClick: () -> Unit,
) {
    entry<PlaylistListRoute> {
        PlaylistListScreenRoute(
            onPlaylistClick = onPlaylistClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

fun EntryProviderScope<NavKey>.playlistDetailEntry(
    onNavigateUp: () -> Unit,
    onPlayPlaylist: (playlistId: Long, startUri: Uri) -> Unit,
) {
    entry<PlaylistDetailRoute> { key ->
        PlaylistDetailScreenRoute(
            onNavigateUp = onNavigateUp,
            onPlayPlaylist = onPlayPlaylist,
            viewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
                creationCallback = { factory -> factory.create(key.playlistId) },
            ),
        )
    }
}
