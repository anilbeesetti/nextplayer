package dev.anilbeesetti.nextplayer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreAction
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreScreenContent
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreScreenHistoryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyAndDisabledHistoryShowTheirMessagesAndKeepHistoryAccessible() {
        val state = mutableStateOf(MoreUiState(history = DataState.Success(emptyList())))
        val actions = mutableListOf<MoreAction>()
        composeRule.setContent {
            NextPlayerTheme {
                MoreScreenContent(state = state.value, onAction = actions::add)
            }
        }

        composeRule.onNodeWithText("No watch history").assertExists()
        composeRule.onNodeWithContentDescription("History").performClick()
        composeRule.runOnIdle {
            state.value = state.value.copy(
                history = DataState.Success(listOf(Video.sample)),
                preferences = state.value.preferences.copy(isHistoryPaused = true),
            )
        }
        composeRule.onNodeWithText("Watch history is turned off").assertExists()
        composeRule.onNodeWithText("No watch history").assertDoesNotExist()
        composeRule.onNodeWithText(Video.sample.displayName).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("History").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(MoreAction.OpenHistory, MoreAction.OpenHistory), actions)
            state.value = state.value.copy(
                history = DataState.Success(emptyList()),
                preferences = state.value.preferences.copy(isHistoryPaused = false),
            )
        }
        composeRule.onNodeWithText("No watch history").assertExists()
        composeRule.onNodeWithText("Watch history is turned off").assertDoesNotExist()
    }
}
