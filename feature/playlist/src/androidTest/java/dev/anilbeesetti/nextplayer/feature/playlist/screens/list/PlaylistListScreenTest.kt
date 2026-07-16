package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createPlaylistFabShowsAllThreeCreationChoices() {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(uiState = PlaylistListUiState(isLoading = false))
            }
        }

        composeRule.onNodeWithContentDescription("Create playlist").performClick()

        composeRule.onNodeWithText("Create empty playlist").assertIsDisplayed()
        composeRule.onNodeWithText("Add M3U playlist from URL").assertIsDisplayed()
        composeRule.onNodeWithText("Add M3U playlist from file").assertIsDisplayed()
    }

    @Test
    fun submittedNameConflictRemainsVisibleInCreationDialog() {
        composeRule.setContent {
            NextPlayerTheme {
                var uiState by remember { mutableStateOf(PlaylistListUiState(isLoading = false)) }
                PlaylistListScreen(
                    uiState = uiState,
                    onAction = { action ->
                        if (action is PlaylistListAction.CreateEditable) {
                            uiState = uiState.copy(formError = "A playlist with this name already exists.")
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Create playlist").performClick()
        composeRule.onNodeWithText("Create empty playlist").performClick()
        composeRule.onNodeWithText("Playlist name").performTextInput("Movies")
        composeRule.onNodeWithText("Create").performClick()

        composeRule.onNodeWithText("A playlist with this name already exists.").assertIsDisplayed()
        composeRule.onNodeWithText("Create empty playlist").assertIsDisplayed()
    }

    @Test
    fun playlistRowsShowTypeAndItemCountOnOneSupportingLine() {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlists = listOf(
                            summary(1, "Movies", PlaylistType.EDITABLE, 1),
                            summary(2, "Streams", PlaylistType.M3U_URL, 2),
                            summary(3, "Imported", PlaylistType.M3U_FILE, 3),
                        ),
                        isLoading = false,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Local · 1 item").assertIsDisplayed()
        composeRule.onNodeWithText("M3U URL · 2 items").assertIsDisplayed()
        composeRule.onNodeWithText("M3U File · 3 items").assertIsDisplayed()
        composeRule.onAllNodesWithText("Editable").assertCountEquals(0)
    }

    @Test
    fun listOverflowDeleteRequiresConfirmation() {
        val actions = mutableListOf<PlaylistListAction>()
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlists = listOf(summary(42, "Movies", PlaylistType.EDITABLE, 1)),
                        isLoading = false,
                    ),
                    onAction = { actions += it },
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Playlist actions")[0].performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Remove \"Movies\"?").assertIsDisplayed()
        assertEquals(emptyList<PlaylistListAction>(), actions)

        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf(PlaylistListAction.Delete(42)), actions)
    }
}

private fun summary(id: Long, name: String, type: PlaylistType, itemCount: Int) = PlaylistSummary(
    id = id,
    name = name,
    type = type,
    itemCount = itemCount,
    lastRefreshedAt = null,
)
