package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createFabShowsSingleLocalPlaylistNameDialog() {
        val createdNames = mutableListOf<String>()
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(isLoading = false),
                    onCreate = createdNames::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Create playlist").performClick()
        composeRule.onNodeWithText("Playlist name").performTextInput("Movies")
        composeRule.onNodeWithText("Create").performClick()

        assertEquals(listOf("Movies"), createdNames)
        composeRule.onAllNodesWithText("M3U URL").assertCountEquals(0)
    }

    @Test
    fun rowShowsLocalCountAndRenameAndDeleteActions() {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlists = listOf(PlaylistSummary(7, "Movies", 2)),
                        isLoading = false,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Local · 2 videos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Playlist actions").performClick()
        composeRule.onNodeWithText("Rename playlist").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun existingRowsRemainVisibleWhileRefreshing() {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreen(
                    uiState = PlaylistListUiState(
                        playlists = listOf(PlaylistSummary(7, "Movies", 2)),
                        isLoading = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Movies").assertIsDisplayed()
        composeRule.onNodeWithText("Local · 2 videos").assertIsDisplayed()
    }
}
