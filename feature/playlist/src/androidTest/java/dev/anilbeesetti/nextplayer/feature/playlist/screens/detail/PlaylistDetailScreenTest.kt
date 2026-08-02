package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resolvedVideoMetadataIsRenderedAndPlayAllUsesOrderedQueue() {
        val playCalls = mutableListOf<Pair<List<Uri>, Uri>>()
        val playlist = playlist(
            item("content://two", "Renamed Two.mp4", "/Moved", 0),
            item("content://one", "One.mp4", "/Movies", 1),
        )

        setContent(playlist, onPlayVideos = { uris, startUri -> playCalls += uris to startUri })

        composeRule.onNodeWithText("Renamed Two").assertIsDisplayed()
        composeRule.onNodeWithText("/Moved").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play all").performClick()

        assertEquals(
            listOf("content://two", "content://one"),
            playCalls.single().first.map(Uri::toString),
        )
        assertEquals("content://two", playCalls.single().second.toString())
    }

    @Test
    fun removeActionRequiresConfirmation() {
        val removedUris = mutableListOf<String>()
        setContent(
            playlist(item("content://one", "One.mp4", "/Movies", 0)),
            onRemoveVideo = removedUris::add,
        )

        composeRule.onNodeWithContentDescription("Playlist actions").performClick()
        composeRule.onNodeWithText("Remove").performClick()
        composeRule.onNodeWithText("Remove “One” from this playlist?").assertIsDisplayed()
        assertEquals(emptyList<String>(), removedUris)

        composeRule.onNodeWithText("Remove").performClick()
        assertEquals(listOf("content://one"), removedUris)
    }

    @Test
    fun inPlaceSearchFiltersMetadataAndCloseRestoresThePlaylist() {
        val playCalls = mutableListOf<Pair<List<Uri>, Uri>>()
        val playlist = playlist(
            item("content://one", "One.mp4", "/Movies", 0),
            item("content://two", "Two.mp4", "/Downloads", 1),
        )
        setContent(
            playlist = playlist,
            onPlayVideos = { uris, startUri -> playCalls += uris to startUri },
        )

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Search playlist").performTextInput("downloads")

        composeRule.onAllNodesWithContentDescription("Play all").assertCountEquals(0)
        composeRule.onAllNodesWithText("One").assertCountEquals(0)
        composeRule.onNodeWithText("Two").assertIsDisplayed()
        composeRule.onNodeWithText("Two").performClick()
        assertEquals(
            listOf("content://one", "content://two"),
            playCalls.single().first.map(Uri::toString),
        )
        assertEquals("content://two", playCalls.single().second.toString())

        composeRule.onNodeWithContentDescription("Close search").performClick()
        composeRule.onNodeWithContentDescription("Play all").assertIsDisplayed()
        composeRule.onNodeWithText("One").assertIsDisplayed()
        composeRule.onNodeWithText("Two").assertIsDisplayed()
    }

    @Test
    fun touchUsesWholeRowsForReorderingWithoutPlayingVideos() {
        val playCalls = mutableListOf<Pair<List<Uri>, Uri>>()
        val playlist = playlist(
            item("content://one", "One.mp4", "/Movies", 0),
            item("content://two", "Two.mp4", "/Movies", 1),
        )

        setContent(
            playlist = playlist,
            isTv = false,
            onPlayVideos = { uris, startUri -> playCalls += uris to startUri },
        )
        composeRule.onAllNodesWithContentDescription("Reorder playlist item").assertCountEquals(0)
        composeRule.onNodeWithText("One").performClick()
        assertEquals(1, playCalls.size)

        composeRule.onNodeWithContentDescription("Reorder playlist").performClick()
        composeRule.onAllNodesWithContentDescription("Play all").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Finish reordering").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Reorder playlist item").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Playlist actions").assertCountEquals(0)
        composeRule.onNodeWithText("One").performClick()
        assertEquals(1, playCalls.size)

        composeRule.onNodeWithContentDescription("Finish reordering").performClick()
        composeRule.onNodeWithContentDescription("Play all").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Reorder playlist item").assertCountEquals(0)
    }

    @Test
    fun tvRowsPlayWithoutReorderControls() {
        val playCalls = mutableListOf<Pair<List<Uri>, Uri>>()
        val playlist = playlist(
            item("content://one", "One.mp4", "/Movies", 0),
            item("content://two", "Two.mp4", "/Movies", 1),
        )
        setContent(
            playlist = playlist,
            isTv = true,
            onPlayVideos = { uris, startUri -> playCalls += uris to startUri },
        )
        composeRule.onAllNodesWithContentDescription("Reorder playlist").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Play").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Move up").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Move down").assertCountEquals(0)

        composeRule.onNodeWithText("One").performClick()
        assertEquals(
            listOf("content://one", "content://two"),
            playCalls.single().first.map(Uri::toString),
        )
        assertEquals("content://one", playCalls.single().second.toString())
    }

    private fun setContent(
        playlist: Playlist,
        isTv: Boolean = false,
        onPlayVideos: (List<Uri>, Uri) -> Unit = { _, _ -> },
        onRemoveVideo: (String) -> Unit = {},
        onReplaceOrder: (List<String>) -> Unit = {},
    ) {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistDetailScreen(
                    uiState = PlaylistDetailUiState(
                        playlist = playlist,
                        isLoading = false,
                        actionsEnabled = true,
                    ),
                    isTv = isTv,
                    onBack = {},
                    onPlayVideos = onPlayVideos,
                    onRemoveVideo = onRemoveVideo,
                    onReplaceOrder = onReplaceOrder,
                )
            }
        }
    }
}

private fun playlist(vararg items: PlaylistItem) = Playlist(
    id = 7,
    name = "Movies",
    items = items.toList(),
)

private fun item(
    uri: String,
    name: String,
    parentPath: String,
    position: Int,
) = PlaylistItem(
    position = position,
    video = Video(
        id = position.toLong(),
        path = "$parentPath/$name",
        parentPath = parentPath,
        duration = 1_000,
        uriString = uri,
        nameWithExtension = name,
        width = 1920,
        height = 1080,
        size = 1_000,
    ),
)
