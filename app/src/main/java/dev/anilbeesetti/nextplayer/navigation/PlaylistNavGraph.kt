package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
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
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onPlayVideos = { uris, startUri ->
            context.startPlayback(uris, startUri = startUri)
        },
    )
}
