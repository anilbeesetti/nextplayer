package dev.anilbeesetti.nextplayer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun menuPausesResumesAndConfirmsClearingHistory() {
        val state = mutableStateOf(HistoryUiState(history = DataState.Success(listOf(Video.sample))))
        val actions = mutableListOf<HistoryAction>()
        composeRule.setContent {
            NextPlayerTheme {
                HistoryScreenContent(state = state.value) { action ->
                    actions += action
                    when (action) {
                        HistoryAction.ToggleHistoryPaused -> state.value = state.value.copy(
                            preferences = state.value.preferences.copy(isHistoryPaused = !state.value.preferences.isHistoryPaused),
                        )
                        HistoryAction.ClearHistory -> state.value = state.value.copy(history = DataState.Success(emptyList()))
                        else -> Unit
                    }
                }
            }
        }

        composeRule.onNodeWithText("Clear history").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Pause history").performClick()
        composeRule.onNodeWithText("Resume history").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Resume history").performClick()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Clear history").performClick()
        composeRule.onNodeWithText("Remove all watched videos from history?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(2, actions.size) }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Clear history").performClick()
        composeRule.onNodeWithText("Clear all").performClick()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Clear history").assertIsNotEnabled()
        composeRule.onNodeWithText("Pause history").performClick()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Resume history").assertExists()
        composeRule.runOnIdle {
            assertEquals(
                listOf(HistoryAction.ToggleHistoryPaused, HistoryAction.ToggleHistoryPaused, HistoryAction.ClearHistory, HistoryAction.ToggleHistoryPaused),
                actions,
            )
        }
    }
}
