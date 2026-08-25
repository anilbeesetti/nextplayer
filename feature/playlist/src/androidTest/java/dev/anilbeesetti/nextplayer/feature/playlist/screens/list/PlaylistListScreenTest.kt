package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createFabEmitsShowCreateDialogAction() {
        val actions = mutableListOf<PlaylistUiAction>()

        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlistsDataState = DataState.Success(emptyList()),
                    ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Create playlist").performClick()

        assertEquals(listOf(PlaylistUiAction.ShowCreationChooser), actions)
    }

    @Test
    fun createDialogEmitsCreateAction() {
        val actions = mutableListOf<PlaylistUiAction>()

        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlistsDataState = DataState.Success(emptyList()),
                        creationDialog = PlaylistCreationDialog.LOCAL_NAME,
                    ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Playlist name").performTextInput("Movies")
        composeRule.onNodeWithText("Create").performClick()

        assertEquals(listOf(PlaylistUiAction.CreateLocal("Movies")), actions)
        composeRule.onAllNodesWithText("M3U URL").assertCountEquals(0)
    }

    @Test
    fun creationChooserOffersLocalUrlAndFileSources() {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlistsDataState = DataState.Success(emptyList()),
                        creationDialog = PlaylistCreationDialog.CHOOSER,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Create local playlist").assertIsDisplayed()
        composeRule.onNodeWithText("Add M3U playlist from URL").assertIsDisplayed()
        composeRule.onNodeWithText("Add M3U playlist from file").assertIsDisplayed()
    }

    @Test
    fun rowShowsLocalCountAndEmitsRenameAction() {
        val playlist = playlistSummary()
        val actions = mutableListOf<PlaylistUiAction>()

        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlistsDataState = DataState.Success(listOf(playlist)),
                    ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Local · 2 videos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Playlist actions").performClick()
        composeRule.onNodeWithText("Rename playlist").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithText("Rename playlist").performClick()

        assertEquals(listOf(PlaylistUiAction.ShowRenameDialogFor(playlist)), actions)
    }

    @Test
    fun deleteDialogEmitsDeleteAction() {
        val playlist = playlistSummary()
        val actions = mutableListOf<PlaylistUiAction>()

        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlistsDataState = DataState.Success(listOf(playlist)),
                        showDeleteDialogFor = playlist,
                    ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf(PlaylistUiAction.Delete(playlist.id)), actions)
    }

    @Test
    fun emptySuccessShowsEmptyState() {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlistsDataState = DataState.Success(emptyList()),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("No playlists yet").assertIsDisplayed()
    }
}

private fun playlistSummary() = PlaylistSummary(
    id = 7,
    name = "Movies",
    type = PlaylistType.LOCAL,
    itemCount = 2,
    lastRefreshedAt = null,
)
