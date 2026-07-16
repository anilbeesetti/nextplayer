package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playAllDispatchesPlaybackAction() {
        val actions = mutableListOf<PlaylistDetailAction>()
        setDetailContent(playlist = playlist(items = listOf(item("content://one", 0)))) {
            actions += it
        }

        composeRule.onNodeWithContentDescription("Play all").performClick()

        assertEquals(listOf(PlaylistDetailAction.PlayAll), actions)
    }

    @Test
    fun linkedPlaylistOffersRefreshWithoutReorderControls() {
        val actions = mutableListOf<PlaylistDetailAction>()
        setDetailContent(
            playlist = playlist(
                type = PlaylistType.M3U_URL,
                items = listOf(item("https://example.test/one.mp4", 0)),
            ),
            onAction = { actions += it },
        )

        composeRule.onNodeWithContentDescription("Refresh playlist").assertIsDisplayed().performClick()
        composeRule.onAllNodesWithContentDescription("Reorder playlist item").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Move up").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Move down").assertCountEquals(0)
        composeRule.onNodeWithText("https://example.test/one.mp4").assertIsDisplayed()
        assertEquals(listOf(PlaylistDetailAction.Refresh), actions)
    }

    @Test
    fun localPlaylistItemShowsPersistedDisplayPath() {
        setDetailContent(
            playlist = playlist(
                items = listOf(
                    item(
                        uri = "content://one",
                        position = 0,
                        displayPath = "/storage/emulated/0/Movies",
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("/storage/emulated/0/Movies").assertIsDisplayed()
    }

    @Test
    fun editableTouchPlaylistShowsDragHandles() {
        setDetailContent(
            playlist = playlist(
                items = listOf(item("content://one", 0), item("content://two", 1)),
            ),
        )

        composeRule.onAllNodesWithContentDescription("Reorder playlist item").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Refresh playlist").assertCountEquals(0)
    }

    @Test
    fun editableTvPlaylistShowsFocusableEdgeAwareMoveButtons() {
        setDetailContent(
            playlist = playlist(
                items = listOf(item("content://one", 0), item("content://two", 1)),
            ),
            isTv = true,
        )

        composeRule.onAllNodesWithContentDescription("Move up")[0].assertIsNotEnabled()
        composeRule.onAllNodesWithContentDescription("Move up")[1].assertIsEnabled()
        composeRule.onAllNodesWithContentDescription("Move down")[0].assertIsEnabled()
        composeRule.onAllNodesWithContentDescription("Move down")[1].assertIsNotEnabled()
    }

    @Test
    fun moveInProgressRemovesTouchReorderAffordances() {
        val editable = playlist(
            items = listOf(item("content://one", 0), item("content://two", 1)),
        )

        setDetailContent(playlist = editable, isMoving = true)

        composeRule.onAllNodesWithContentDescription("Reorder playlist item").assertCountEquals(0)
    }

    @Test
    fun moveInProgressRemovesTvReorderAffordances() {
        val editable = playlist(
            items = listOf(item("content://one", 0), item("content://two", 1)),
        )

        setDetailContent(playlist = editable, isMoving = true, isTv = true)

        composeRule.onAllNodesWithContentDescription("Move up").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Move down").assertCountEquals(0)
    }

    @Test
    fun refreshProgressKeepsLinkedItemsVisible() {
        setDetailContent(
            playlist = playlist(
                type = PlaylistType.M3U_FILE,
                items = listOf(item("content://cached", 0, title = "Cached item")),
            ),
            isRefreshing = true,
        )

        composeRule.onNodeWithText("Cached item").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Refreshing playlist").assertIsDisplayed()
    }

    @Test
    fun detailDoesNotOfferPlaylistDeletion() {
        setDetailContent(playlist = playlist())

        composeRule.onAllNodesWithContentDescription("Delete playlist").assertCountEquals(0)
    }

    private fun setDetailContent(
        playlist: Playlist,
        isRefreshing: Boolean = false,
        isMoving: Boolean = false,
        isTv: Boolean = false,
        onAction: (PlaylistDetailAction) -> Unit = {},
    ) {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistDetailScreen(
                    uiState = PlaylistDetailUiState(
                        playlist = playlist,
                        isLoading = false,
                        isRefreshing = isRefreshing,
                        isMoving = isMoving,
                    ),
                    isTv = isTv,
                    onBack = {},
                    onAction = onAction,
                )
            }
        }
    }
}

private fun playlist(
    type: PlaylistType = PlaylistType.EDITABLE,
    items: List<PlaylistItem> = emptyList(),
) = Playlist(
    id = 42,
    name = "Movies",
    type = type,
    source = if (type == PlaylistType.EDITABLE) null else "source",
    items = items,
    lastRefreshedAt = if (type == PlaylistType.EDITABLE) null else 1_700_000_000_000,
)

private fun item(
    uri: String,
    position: Int,
    title: String = "Item ${position + 1}",
    imageUrl: String? = null,
    displayPath: String? = null,
) = PlaylistItem(
    uriString = uri,
    title = title,
    position = position,
    imageUrl = imageUrl,
    displayPath = displayPath,
)
