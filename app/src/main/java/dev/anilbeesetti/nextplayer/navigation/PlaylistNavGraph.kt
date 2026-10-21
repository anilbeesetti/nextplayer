package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.PlaylistShortcut
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.utils.PlaylistPlaybackContract
import dev.anilbeesetti.nextplayer.feature.playlist.navigation.PlaylistDetailRoute
import dev.anilbeesetti.nextplayer.feature.playlist.navigation.PlaylistListRoute
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
        onAddToHomeScreen = if (PlaylistShortcut.isSupported(context)) {
            { playlist ->
                if (!PlaylistShortcut.requestPin(context, playlist)) {
                    Toast.makeText(context, R.string.playlist_shortcut_failed, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            null
        },
    )

    playlistDetailEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onPlayPlaylist = { playlistId, startUri ->
            context.startPlaylistPlayback(
                playlistId = playlistId,
                startUri = startUri,
            )
        },
    )
}

internal fun TopLevelNavState.openPlaylist(playlistId: Long) {
    val playlistStack = backStacks.getValue(PlaylistListRoute)
    playlistStack.clear()
    playlistStack.add(PlaylistListRoute)
    playlistStack.add(PlaylistDetailRoute(playlistId))
    switchTo(PlaylistListRoute)
}

internal fun Context.startPlaylistPlayback(
    playlistId: Long,
    startUri: Uri,
) {
    startActivity(
        Intent(this, PlayerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = startUri
            putExtra(PlaylistPlaybackContract.EXTRA_PLAYLIST_ID, playlistId)
        },
    )
}
