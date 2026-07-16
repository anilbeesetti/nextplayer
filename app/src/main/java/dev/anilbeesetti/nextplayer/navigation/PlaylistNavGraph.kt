package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.utils.PlaylistPlaybackContract
import dev.anilbeesetti.nextplayer.feature.playlist.navigation.navigateToPlaylistDetail
import dev.anilbeesetti.nextplayer.feature.playlist.navigation.playlistDetailEntry
import dev.anilbeesetti.nextplayer.feature.playlist.navigation.playlistListEntry
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings

fun EntryProviderScope<NavKey>.playlistNavGraph(
    context: Context,
    backStack: NavBackStack<NavKey>,
) {
    playlistListEntry(
        onPlaylistClick = backStack::navigateToPlaylistDetail,
        onSettingsClick = backStack::navigateToSettings,
    )

    playlistDetailEntry(
        onNavigateUp = { backStack.removeLastOrNull() },
        onPlayPlaylist = { playlistId, startUri ->
            context.startPlaylistPlayback(playlistId, startUri)
        },
    )
}

private fun Context.startPlaylistPlayback(
    playlistId: Long,
    startUri: Uri,
) {
    val spec = playlistPlaybackLaunchSpec(playlistId, startUri)
    startActivity(
        Intent(this, PlayerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = spec.startItem
            putExtra(PlaylistPlaybackContract.EXTRA_PLAYLIST_ID, spec.playlistId)
        },
    )
}

internal data class PlaylistPlaybackLaunchSpec<T>(
    val playlistId: Long,
    val startItem: T,
)

internal fun <T> playlistPlaybackLaunchSpec(
    playlistId: Long,
    startItem: T,
) = PlaylistPlaybackLaunchSpec(playlistId, startItem)
