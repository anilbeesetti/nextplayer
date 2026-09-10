package dev.anilbeesetti.nextplayer.feature.playlist.navigation

import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.playlist.screens.detail.PlaylistDetailRoute
import dev.anilbeesetti.nextplayer.feature.playlist.screens.detail.PlaylistDetailViewModel
import dev.anilbeesetti.nextplayer.feature.playlist.screens.list.PlaylistListRoute
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
        PlaylistListRoute(output = PlaylistListViewModel.Output(openPlaylist = onPlaylistClick, openSettings = onSettingsClick))
    }
}

fun EntryProviderScope<NavKey>.playlistDetailEntry(
    onNavigateUp: () -> Unit,
    onPlayPlaylist: (playlistId: Long, startUri: Uri) -> Unit,
) {
    entry<PlaylistDetailRoute> { route ->
        PlaylistDetailRoute(
            input = PlaylistDetailViewModel.Input(playlistId = route.playlistId),
            output = PlaylistDetailViewModel.Output(
                navigateUp = onNavigateUp,
                playPlaylist = onPlayPlaylist,
            ),
        )
    }
}
