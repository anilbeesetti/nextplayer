package com.graviton.navigation

import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.graviton.feature.playlist.navigation.PlaylistDetailRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelNavigationTest {

    @Test
    fun playlistsIsTheMiddleTopLevelDestination() {
        assertEquals(
            listOf(
                TopLevelDestination.MEDIA,
                TopLevelDestination.PLAYLISTS,
                TopLevelDestination.MUSIC,
                TopLevelDestination.NETWORK,
            ),
            TopLevelDestination.entries,
        )
    }

    @Test
    fun switchingTabsPreservesPlaylistDetailStack() {
        val stacks = TopLevelDestination.entries.associate { destination ->
            destination.route to NavBackStack<NavKey>(destination.route)
        }
        val state = TopLevelNavState(
            destinations = TopLevelDestination.entries,
            backStacks = stacks,
            selectedIndexState = mutableIntStateOf(0),
        )
        val playlistStack = stacks.getValue(TopLevelDestination.PLAYLISTS.route)

        state.switchTo(TopLevelDestination.PLAYLISTS.route)
        playlistStack += PlaylistDetailRoute(7)
        state.switchTo(TopLevelDestination.MEDIA.route)
        state.switchTo(TopLevelDestination.PLAYLISTS.route)

        assertEquals(
            listOf(TopLevelDestination.PLAYLISTS.route, PlaylistDetailRoute(7)),
            state.currentStack,
        )
    }
}
