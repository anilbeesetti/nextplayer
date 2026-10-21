package dev.anilbeesetti.nextplayer.navigation

import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.feature.playlist.navigation.PlaylistDetailRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelNavigationTest {

    @Test
    fun topLevelDestinationsAreInOrder() {
        assertEquals(
            listOf(
                TopLevelDestination.MEDIA,
                TopLevelDestination.PLAYLISTS,
                TopLevelDestination.NETWORK,
                TopLevelDestination.MORE,
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

    @Test
    fun shortcutSelectsPlaylistAndReplacesPreviousPlaylistStack() {
        val stacks = TopLevelDestination.entries.associate { destination ->
            destination.route to NavBackStack<NavKey>(destination.route)
        }
        val state = TopLevelNavState(
            destinations = TopLevelDestination.entries,
            backStacks = stacks,
            selectedIndexState = mutableIntStateOf(0),
        )
        stacks.getValue(TopLevelDestination.PLAYLISTS.route).add(PlaylistDetailRoute(7))
        state.switchTo(TopLevelDestination.NETWORK.route)

        state.openPlaylist(42)
        state.openPlaylist(42)

        assertEquals(TopLevelDestination.PLAYLISTS.route, state.topLevelRoute)
        assertEquals(listOf(TopLevelDestination.PLAYLISTS.route, PlaylistDetailRoute(42)), state.currentStack)
        state.goBack()
        assertEquals(listOf(TopLevelDestination.PLAYLISTS.route), state.currentStack)
        state.goBack()
        assertEquals(TopLevelDestination.MEDIA.route, state.topLevelRoute)
    }

    @Test
    fun topLevelContentKeysContainsDestinations() {
        val stacks = TopLevelDestination.entries.associate { destination ->
            destination.route to NavBackStack<NavKey>(destination.route)
        }
        val state = TopLevelNavState(
            destinations = TopLevelDestination.entries,
            backStacks = stacks,
            selectedIndexState = mutableIntStateOf(0),
        )
        for (dest in TopLevelDestination.entries) {
            assert(state.topLevelContentKeys.contains(dest.route))
            assert(state.topLevelContentKeys.contains(Pair("${dest.route}", "${dest.route::class}")))
        }
    }
}
