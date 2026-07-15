package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
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
}
