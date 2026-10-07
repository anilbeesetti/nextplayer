package dev.anilbeesetti.nextplayer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryAction
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryScreenContent
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun turningHistoryOffAndOnRequiresConfirmation() {
        val state = mutableStateOf(HistoryUiState(history = DataState.Success(listOf(Video.sample))))
        val actions = mutableListOf<HistoryAction>()
        composeRule.setContent {
            NextPlayerTheme {
                HistoryScreenContent(state = state.value) { action ->
                    actions += action
                    if (action is HistoryAction.SetHistoryEnabled) {
                        state.value = state.value.copy(
                            preferences = state.value.preferences.copy(isHistoryPaused = !action.enabled),
                            history = DataState.Success(emptyList()),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Turn off watch history").performClick()
        composeRule.onNodeWithText("Turning off watch history will clear your watch history and stop recording videos you watch.").assertExists()
        composeRule.runOnIdle { assertEquals(emptyList<HistoryAction>(), actions) }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(emptyList<HistoryAction>(), actions) }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Turn off watch history").performClick()
        confirm("Turn off watch history")
        composeRule.onNodeWithText("Watch history is turned off").assertExists()
        composeRule.runOnIdle { assertEquals(listOf(HistoryAction.SetHistoryEnabled(false)), actions) }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Clear history").assertIsNotEnabled()
        composeRule.onNodeWithText("Turn on watch history").performClick()
        composeRule.onNodeWithText("Start recording videos you watch in your watch history?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Watch history is turned off").assertExists()
        composeRule.runOnIdle { assertEquals(1, actions.size) }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Turn on watch history").performClick()
        confirm("Turn on watch history")
        composeRule.onNodeWithText("No watch history").assertExists()
        composeRule.runOnIdle { assertEquals(listOf(HistoryAction.SetHistoryEnabled(false), HistoryAction.SetHistoryEnabled(true)), actions) }
    }

    @Test
    fun clearHistoryRequiresConfirmationAndShowsEmptyState() {
        val state = mutableStateOf(HistoryUiState(history = DataState.Success(listOf(Video.sample))))
        val actions = mutableListOf<HistoryAction>()
        composeRule.setContent {
            NextPlayerTheme {
                HistoryScreenContent(state = state.value) { action ->
                    actions += action
                    if (action == HistoryAction.ClearHistory) state.value = state.value.copy(history = DataState.Success(emptyList()))
                }
            }
        }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Clear history").performClick()
        composeRule.onNodeWithText("Remove all watched videos from history?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(emptyList<HistoryAction>(), actions) }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Clear history").performClick()
        composeRule.onNodeWithText("Clear all").performClick()
        composeRule.onNodeWithText("No watch history").assertExists()
        composeRule.runOnIdle { assertEquals(listOf(HistoryAction.ClearHistory), actions) }
    }

    @Test
    fun disabledHistoryHidesExistingEntries() {
        composeRule.setContent {
            NextPlayerTheme {
                HistoryScreenContent(
                    state = HistoryUiState(
                        history = DataState.Success(listOf(Video.sample)),
                        preferences = ApplicationPreferences(isHistoryPaused = true),
                    ),
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithText("Watch history is turned off").assertExists()
        composeRule.onNodeWithText(Video.sample.displayName).assertDoesNotExist()
    }

    private fun confirm(text: String) {
        composeRule.onNode(hasText(text) and hasClickAction()).performClick()
    }
}
