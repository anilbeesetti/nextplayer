package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import android.content.Context
import android.net.Uri
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.ActionState
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class PlaylistDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tvFabUpReturnsToTheVideoInsteadOfSearch() {
        assumeTrue(ApplicationProvider.getApplicationContext<Context>().isTelevision)
        setContent(
            playlist = playlist(
                item(
                    uri = "content://one",
                    name = "One.mp4",
                    parentPath = "/Movies",
                    position = 0,
                ),
            ),
            isTv = true,
        )
        composeRule.onNodeWithContentDescription(label = "Play").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText(text = "One").assertIsFocused()
    }

    @Test
    fun tvFabUpRestoresThePreviouslyFocusedVideo() {
        assumeTrue(ApplicationProvider.getApplicationContext<Context>().isTelevision)
        setContent(
            playlist = playlist(
                item(
                    uri = "content://one",
                    name = "One.mp4",
                    parentPath = "/Movies",
                    position = 0,
                ),
                item(
                    uri = "content://two",
                    name = "Two.mp4",
                    parentPath = "/Movies",
                    position = 1,
                ),
            ),
            isTv = true,
        )
        composeRule.onNodeWithText(text = "Two").requestFocus()
        composeRule.onNodeWithContentDescription(label = "Play").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText(text = "Two").assertIsFocused()
    }

    @Test
    fun playFabEmitsOrderedQueueStartingFromFirstVideo() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item(
                uri = "content://two",
                name = "Renamed Two.mp4",
                parentPath = "/Moved",
                position = 0,
            ),
            item(
                uri = "content://one",
                name = "One.mp4",
                parentPath = "/Movies",
                position = 1,
            ),
        )

        setContent(
            playlist = playlist,
            onAction = actions::add,
        )

        composeRule.onNodeWithText(text = "Renamed Two").assertIsDisplayed()
        composeRule.onNodeWithText(text = "/Moved").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(
            label = "Play all",
        ).assertCountEquals(expectedSize = 0)
        composeRule.onNodeWithContentDescription(label = "Play").performClick()

        assertEquals(
            PlaylistDetailUiAction.OnPlay(Uri.parse("content://two")),
            actions.single(),
        )
    }

    @Test
    fun playFabStartsFromTheLastPlayedVideo() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item(
                uri = "content://one",
                name = "One.mp4",
                parentPath = "/Movies",
                position = 0,
            ),
            item(
                uri = "content://two",
                name = "Two.mp4",
                parentPath = "/Movies",
                position = 1,
                lastPlayedAt = 200,
            ),
        )

        setContent(
            playlist = playlist,
            onAction = actions::add,
        )
        composeRule.onNodeWithContentDescription(label = "Play").performClick()

        assertEquals(
            PlaylistDetailUiAction.OnPlay(Uri.parse("content://two")),
            actions.single(),
        )
    }

    @Test
    fun existingPlaylistRemainsVisibleWhileUpdating() {
        setContent(
            playlist = playlist(
                item(
                    uri = "content://one",
                    name = "One.mp4",
                    parentPath = "/Movies",
                    position = 0,
                ),
            ),
            updateActionState = ActionState.Running,
        )

        composeRule.onNodeWithText(text = "One").assertIsDisplayed()
        composeRule.onNodeWithText(text = "/Movies").assertIsDisplayed()
    }

    @Test
    fun removeMenuRequestsConfirmation() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val item = item(
            uri = "content://one",
            name = "One.mp4",
            parentPath = "/Movies",
            position = 0,
        )
        setContent(
            playlist = playlist(item),
            onAction = actions::add,
        )

        composeRule.onNodeWithContentDescription(label = "Playlist actions").performClick()
        composeRule.onNodeWithText(text = "Remove").performClick()

        assertEquals(
            listOf(PlaylistDetailUiAction.ShowRemoveDialogFor(item = item)),
            actions,
        )
    }

    @Test
    fun removeDialogEmitsRemoveActionAfterConfirmation() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val item = item(
            uri = "content://one",
            name = "One.mp4",
            parentPath = "/Movies",
            position = 0,
        )
        setContent(
            playlist = playlist(item),
            showRemoveDialogFor = item,
            onAction = actions::add,
        )

        composeRule.onNodeWithText(text = "Remove “One” from this playlist?").assertIsDisplayed()
        composeRule.onNodeWithText(text = "Remove").performClick()

        assertEquals(
            listOf(PlaylistDetailUiAction.RemoveVideo(videoUri = "content://one")),
            actions,
        )
    }

    @Test
    fun inPlaceSearchFiltersMetadataAndEmitsPlaybackAction() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item(
                uri = "content://one",
                name = "One.mp4",
                parentPath = "/Movies",
                position = 0,
            ),
            item(
                uri = "content://two",
                name = "Two.mp4",
                parentPath = "/Downloads",
                position = 1,
            ),
        )
        setContent(
            playlist = playlist,
            isSearching = true,
            searchQuery = "downloads",
            onAction = actions::add,
        )

        composeRule.onAllNodesWithContentDescription(
            label = "Play",
        ).assertCountEquals(expectedSize = 0)
        composeRule.onAllNodesWithText(text = "One").assertCountEquals(expectedSize = 0)
        composeRule.onNodeWithText(text = "Two").assertIsDisplayed()
        composeRule.onNodeWithText(text = "Two").performClick()

        assertEquals(
            PlaylistDetailUiAction.OnPlay(Uri.parse("content://two")),
            actions.single(),
        )
    }

    @Test
    fun closeSearchEmitsCloseAction() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        setContent(
            playlist = playlist(
                item(
                    uri = "content://one",
                    name = "One.mp4",
                    parentPath = "/Movies",
                    position = 0,
                ),
            ),
            isSearching = true,
            onAction = actions::add,
        )

        composeRule.onNodeWithContentDescription(label = "Close search").performClick()

        assertEquals(
            listOf(element = PlaylistDetailUiAction.OnCloseSearchClick),
            actions,
        )
    }

    @Test
    fun touchReorderModeUsesWholeRowsWithoutPlayingVideos() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item(
                uri = "content://one",
                name = "One.mp4",
                parentPath = "/Movies",
                position = 0,
            ),
            item(
                uri = "content://two",
                name = "Two.mp4",
                parentPath = "/Movies",
                position = 1,
            ),
        )

        setContent(
            playlist = playlist,
            isTv = false,
            isReordering = true,
            onAction = actions::add,
        )

        composeRule.onAllNodesWithContentDescription(
            label = "Play",
        ).assertCountEquals(expectedSize = 0)
        composeRule.onNodeWithContentDescription(label = "Finish reordering").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(
            label = "Reorder playlist item",
        ).assertCountEquals(expectedSize = 2)
        composeRule.onAllNodesWithContentDescription(
            label = "Playlist actions",
        ).assertCountEquals(expectedSize = 0)
        composeRule.onNodeWithText(text = "One").performClick()
        assertEquals(
            emptyList<PlaylistDetailUiAction>(),
            actions,
        )

        composeRule.onNodeWithContentDescription(label = "Finish reordering").performClick()
        assertEquals(
            listOf(element = PlaylistDetailUiAction.OnFinishReorderingClick),
            actions,
        )
    }

    @Test
    fun reorderButtonEmitsReorderAction() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        setContent(
            playlist = playlist(
                item(
                    uri = "content://one",
                    name = "One.mp4",
                    parentPath = "/Movies",
                    position = 0,
                ),
                item(
                    uri = "content://two",
                    name = "Two.mp4",
                    parentPath = "/Movies",
                    position = 1,
                ),
            ),
            onAction = actions::add,
        )

        composeRule.onNodeWithContentDescription(label = "Reorder playlist").performClick()

        assertEquals(
            listOf(element = PlaylistDetailUiAction.OnReorderClick),
            actions,
        )
    }

    @Test
    fun tvRowsPlayWithoutReorderControls() {
        val actions = mutableListOf<PlaylistDetailUiAction>()
        val playlist = playlist(
            item(
                uri = "content://one",
                name = "One.mp4",
                parentPath = "/Movies",
                position = 0,
            ),
            item(
                uri = "content://two",
                name = "Two.mp4",
                parentPath = "/Movies",
                position = 1,
            ),
        )
        setContent(
            playlist = playlist,
            isTv = true,
            onAction = actions::add,
        )

        composeRule.onAllNodesWithContentDescription(
            label = "Reorder playlist",
        ).assertCountEquals(expectedSize = 0)
        composeRule.onAllNodesWithContentDescription(
            label = "Play",
        ).assertCountEquals(expectedSize = 1)
        composeRule.onAllNodesWithContentDescription(
            label = "Move up",
        ).assertCountEquals(expectedSize = 0)
        composeRule.onAllNodesWithContentDescription(
            label = "Move down",
        ).assertCountEquals(expectedSize = 0)

        composeRule.onNodeWithText(text = "One").performClick()
        assertEquals(
            PlaylistDetailUiAction.OnPlay(Uri.parse("content://one")),
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "Requires ${if (isTv) "TV" else "phone"} device",
            context.isTelevision == isTv,
        )
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistDetailScreenContent(
                    state = PlaylistDetailUiState(
                        playlistDataState = DataState.Success(value = playlist),
                        updateActionState = updateActionState,
                        isSearching = isSearching,
                        searchQuery = searchQuery,
                        isReordering = isReordering,
                        showRemoveDialogFor = showRemoveDialogFor,
                    ),
                    onAction = onAction,
                )
            }
        }
    }
}

private fun playlist(vararg items: PlaylistItem) = Playlist(
    id = 7,
    name = "Movies",
    type = PlaylistType.LOCAL,
    source = null,
    items = items.toList(),
    lastRefreshedAt = null,
)

private fun item(
    uri: String,
    name: String,
    parentPath: String,
    position: Int,
    lastPlayedAt: Long? = null,
) = PlaylistItem(
    position = position,
    uri = uri,
    title = null,
    tvgLogo = null,
    duration = -1,
    groupTitle = null,
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
