package com.graviton.feature.playlist.screens.detail

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.graviton.core.model.Playlist
import com.graviton.core.model.PlaylistItem
import com.graviton.core.model.Video
import com.graviton.core.ui.base.ActionState
import com.graviton.core.ui.base.DataState
import com.graviton.core.ui.theme.GravitonTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playFabEmitsOrderedQueueStartingFromFirstVideo() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item("content://two", "Renamed Two.mp4", "/Moved", 0),
            item("content://one", "One.mp4", "/Movies", 1),
        )

        setContent(playlist, onAction = actions::add)

        composeRule.onNodeWithText("Renamed Two").assertIsDisplayed()
        composeRule.onNodeWithText("/Moved").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Play all").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Play").performClick()

        assertEquals(
            PlaylistDetailUiAction.OnPlayVideos(
                uris = listOf("content://two", "content://one").map(Uri::parse),
                startUri = Uri.parse("content://two"),
            ),
            actions.single(),
        )
    }

    @Test
    fun playFabStartsFromTheLastPlayedVideo() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item("content://one", "One.mp4", "/Movies", 0),
            item("content://two", "Two.mp4", "/Movies", 1, lastPlayedAt = 200),
        )

        setContent(playlist, onAction = actions::add)
        composeRule.onNodeWithContentDescription("Play").performClick()

        assertEquals(
            PlaylistDetailUiAction.OnPlayVideos(
                uris = listOf("content://one", "content://two").map(Uri::parse),
                startUri = Uri.parse("content://two"),
            ),
            actions.single(),
        )
    }

    @Test
    fun existingPlaylistRemainsVisibleWhileUpdating() {
        setContent(
            playlist = playlist(item("content://one", "One.mp4", "/Movies", 0)),
            updateActionState = ActionState.Running,
        )

        composeRule.onNodeWithText("One").assertIsDisplayed()
        composeRule.onNodeWithText("/Movies").assertIsDisplayed()
    }

    @Test
    fun removeMenuRequestsConfirmation() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val item = item("content://one", "One.mp4", "/Movies", 0)
        setContent(
            playlist = playlist(item),
            onAction = actions::add,
        )

        composeRule.onNodeWithContentDescription("Playlist actions").performClick()
        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(
            listOf(PlaylistDetailUiAction.ShowRemoveDialogFor(item)),
            actions,
        )
    }

    @Test
    fun removeDialogEmitsRemoveActionAfterConfirmation() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val item = item("content://one", "One.mp4", "/Movies", 0)
        setContent(
            playlist = playlist(item),
            showRemoveDialogFor = item,
            onAction = actions::add,
        )

        composeRule.onNodeWithText("Remove “One” from this playlist?").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(
            listOf(PlaylistDetailUiAction.RemoveVideo("content://one")),
            actions,
        )
    }

    @Test
    fun inPlaceSearchFiltersMetadataAndEmitsPlaybackAction() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item("content://one", "One.mp4", "/Movies", 0),
            item("content://two", "Two.mp4", "/Downloads", 1),
        )
        setContent(
            playlist = playlist,
            isSearching = true,
            searchQuery = "downloads",
            onAction = actions::add,
        )

        composeRule.onAllNodesWithContentDescription("Play").assertCountEquals(0)
        composeRule.onAllNodesWithText("One").assertCountEquals(0)
        composeRule.onNodeWithText("Two").assertIsDisplayed()
        composeRule.onNodeWithText("Two").performClick()

        assertEquals(
            PlaylistDetailUiAction.OnPlayVideos(
                uris = listOf("content://one", "content://two").map(Uri::parse),
                startUri = Uri.parse("content://two"),
            ),
            actions.single(),
        )
    }

    @Test
    fun closeSearchEmitsCloseAction() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        setContent(
            playlist = playlist(item("content://one", "One.mp4", "/Movies", 0)),
            isSearching = true,
            onAction = actions::add,
        )

        composeRule.onNodeWithContentDescription("Close search").performClick()

        assertEquals(
            listOf(PlaylistDetailUiAction.OnCloseSearchClick),
            actions,
        )
    }

    @Test
    fun touchReorderModeUsesWholeRowsWithoutPlayingVideos() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item("content://one", "One.mp4", "/Movies", 0),
            item("content://two", "Two.mp4", "/Movies", 1),
        )

        setContent(
            playlist = playlist,
            isTv = false,
            isReordering = true,
            onAction = actions::add,
        )

        composeRule.onAllNodesWithContentDescription("Play").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Finish reordering").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Reorder playlist item").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Playlist actions").assertCountEquals(0)
        composeRule.onNodeWithText("One").performClick()
        assertEquals(emptyList<PlaylistDetailUiAction>(), actions)

        composeRule.onNodeWithContentDescription("Finish reordering").performClick()
        assertEquals(
            listOf(PlaylistDetailUiAction.OnFinishReorderingClick),
            actions,
        )
    }

    @Test
    fun reorderButtonEmitsReorderAction() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        setContent(
            playlist = playlist(
                item("content://one", "One.mp4", "/Movies", 0),
                item("content://two", "Two.mp4", "/Movies", 1),
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithContentDescription("Reorder playlist").performClick()

        assertEquals(
            listOf(PlaylistDetailUiAction.OnReorderClick),
            actions,
        )
    }

    @Test
    fun tvRowsPlayWithoutReorderControls() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item("content://one", "One.mp4", "/Movies", 0),
            item("content://two", "Two.mp4", "/Movies", 1),
        )
        setContent(
            playlist = playlist,
            isTv = true,
            onAction = actions::add,
        )

        composeRule.onAllNodesWithContentDescription("Reorder playlist").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Play").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Move up").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Move down").assertCountEquals(0)

        composeRule.onNodeWithText("One").performClick()
        assertEquals(
            PlaylistDetailUiAction.OnPlayVideos(
                uris = listOf("content://one", "content://two").map(Uri::parse),
                startUri = Uri.parse("content://one"),
            ),
            actions.single(),
        )
    }

    private fun setContent(
        playlist: Playlist,
        isTv: Boolean = false,
        updateActionState: ActionState = ActionState.Idle,
        isSearching: Boolean = false,
        searchQuery: String = "",
        isReordering: Boolean = false,
        showRemoveDialogFor: PlaylistItem? = null,
        onAction: (PlaylistDetailUiAction) -> Unit = {},
    ) {
        composeRule.setContent {
            GravitonTheme {
                PlaylistDetailScreen(
                    uiState = PlaylistDetailUiState(
                        playlistDataState = DataState.Success(playlist),
                        updateActionState = updateActionState,
                        isSearching = isSearching,
                        searchQuery = searchQuery,
                        isReordering = isReordering,
                        showRemoveDialogFor = showRemoveDialogFor,
                    ),
                    isTv = isTv,
                    onAction = onAction,
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
    lastPlayedAt: Long? = null,
) = PlaylistItem(
    position = position,
    lastPlayedAt = lastPlayedAt,
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
