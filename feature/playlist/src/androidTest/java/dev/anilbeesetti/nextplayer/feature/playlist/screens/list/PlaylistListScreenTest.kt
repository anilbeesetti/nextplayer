package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.components.LocalTopLevelFabSetter
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabKey
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabState
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
        var fabState: TopLevelFabState? = null

        composeRule.setContent {
            NextPlayerTheme {
                CompositionLocalProvider(
                    value = LocalTopLevelFabSetter provides { key, state ->
                        if (key == TopLevelFabKey.PLAYLISTS) fabState = state
                    },
                ) {
                    PlaylistListScreenContent(
                        state = PlaylistListUiState(
                            playlistsDataState = DataState.Success(value = emptyList()),
                        ),
                        onAction = actions::add,
                    )
                }
            }
        }

        composeRule.runOnIdle { checkNotNull(value = fabState).onClick() }

        assertEquals(
            listOf(element = PlaylistUiAction.ShowCreationChooser),
            actions,
        )
    }

    @Test
    fun createDialogEmitsCreateAction() {
        val actions = mutableListOf<PlaylistUiAction>()

        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreenContent(
                    state = PlaylistListUiState(
                        playlistsDataState = DataState.Success(value = emptyList()),
                        creationDialog = PlaylistCreationDialog.LOCAL_NAME,
                    ),
                    onAction = actions::add,
                )
            }
        }

        val nameField = composeRule.onNodeWithText(text = "Playlist name")
        // On TV the field is selected but read-only until the remote activates it.
        if (ApplicationProvider.getApplicationContext<Context>().isTelevision) {
            nameField.performKeyInput { pressKey(Key.DirectionCenter) }
        }
        nameField.performTextInput(text = "Movies")
        composeRule.onNodeWithText(text = "Create").performClick()

        assertEquals(
            listOf(PlaylistUiAction.CreateLocal(name = "Movies")),
            actions,
        )
        composeRule.onAllNodesWithText(text = "M3U URL").assertCountEquals(expectedSize = 0)
    }

    @Test
    fun creationChooserOffersLocalUrlAndFileSources() {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreenContent(
                    state = PlaylistListUiState(
                        playlistsDataState = DataState.Success(value = emptyList()),
                        creationDialog = PlaylistCreationDialog.CHOOSER,
                    ),
                )
            }
        }

        composeRule.onNodeWithText(text = "Create local playlist").assertIsDisplayed()
        composeRule.onNodeWithText(text = "Add M3U playlist from URL").assertIsDisplayed()
        composeRule.onNodeWithText(text = "Add M3U playlist from file").assertIsDisplayed()
    }

    @Test
    fun rowShowsLocalCountAndEmitsRenameAction() {
        val playlist = playlistSummary()
        val actions = mutableListOf<PlaylistUiAction>()

        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreenContent(
                    state = PlaylistListUiState(
                        playlistsDataState = DataState.Success(value = listOf(element = playlist)),
                    ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText(text = "Local · 2 videos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(label = "Playlist actions").performClick()
        composeRule.onNodeWithText(text = "Rename playlist").assertIsDisplayed()
        composeRule.onNodeWithText(text = "Delete").assertIsDisplayed()
        composeRule.onNodeWithText(text = "Rename playlist").performClick()

        assertEquals(
            listOf(PlaylistUiAction.ShowRenameDialogFor(playlist = playlist)),
            actions,
        )
    }

    @Test
    fun shortcutActionUsesTheSelectedPlaylist() {
        val playlist = playlistSummary()
        var selectedPlaylist: PlaylistSummary? = null
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreenContent(
                    state = PlaylistListUiState(
                        playlistsDataState = DataState.Success(value = listOf(element = playlist)),
                    ),
                    onAddToHomeScreen = { selectedPlaylist = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription(label = "Playlist actions").performClick()
        composeRule.onNodeWithText(text = "Add to home screen").performClick()

        composeRule.runOnIdle {
            assertEquals(
                playlist,
                selectedPlaylist,
            )
        }
        composeRule.onAllNodesWithText(
            text = "Add to home screen",
        ).assertCountEquals(expectedSize = 0)
    }

    @Test
    fun unsupportedLaunchersDoNotOfferShortcutAction() {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreenContent(
                    state = PlaylistListUiState(
                        playlistsDataState = DataState.Success(
                            value = listOf(element = playlistSummary()),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription(label = "Playlist actions").performClick()

        composeRule.onAllNodesWithText(
            text = "Add to home screen",
        ).assertCountEquals(expectedSize = 0)
    }

    @Test
    fun deleteDialogEmitsDeleteAction() {
        val playlist = playlistSummary()
        val actions = mutableListOf<PlaylistUiAction>()

        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreenContent(
                    state = PlaylistListUiState(
                        playlistsDataState = DataState.Success(value = listOf(element = playlist)),
                        showDeleteDialogFor = playlist,
                    ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText(text = "Delete").performClick()

        assertEquals(
            listOf(PlaylistUiAction.Delete(playlist.id)),
            actions,
        )
    }

    @Test
    fun emptySuccessShowsEmptyState() {
        composeRule.setContent {
            NextPlayerTheme {
                PlaylistListScreenContent(
                    state = PlaylistListUiState(
                        playlistsDataState = DataState.Success(value = emptyList()),
                    ),
                )
            }
        }

        composeRule.onNodeWithText(text = "No playlists yet").assertIsDisplayed()
    }
}

private fun playlistSummary() = PlaylistSummary(
    id = 7,
    name = "Movies",
    type = PlaylistType.LOCAL,
    itemCount = 2,
    lastRefreshedAt = null,
)
