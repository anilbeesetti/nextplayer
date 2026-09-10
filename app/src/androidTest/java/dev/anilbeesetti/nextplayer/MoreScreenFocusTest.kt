package dev.anilbeesetti.nextplayer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreScreenContent
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreUiState
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreScreenFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf(MoreUiState(history = DataState.Success(clips)))

    @Before
    fun requireTv() {
        assumeTrue(composeRule.activity.isTelevision)
    }

    @Test
    fun enteringMoreFocusesVault() {
        showMore()
        composeRule.onNodeWithText("Vault").assertIsFocused()
    }

    @Test
    fun emptyHistoryKeepsTheFirstActionFocused() {
        showMore(emptyList())
        composeRule.onNodeWithText("Vault").assertIsFocused()
        composeRule.onNodeWithContentDescription("History").assertDoesNotExist()
    }

    @Test
    fun downFromTrashVisitsHistoryArrowBeforeVideos() {
        showMore()
        composeRule.onNodeWithText("Trash").requestFocus()
        press(Key.DirectionDown)
        composeRule.onNodeWithContentDescription("History").assertIsFocused()
        press(Key.DirectionDown)
        composeRule.onNodeWithText("Clip 01").assertIsFocused()
    }

    @Test
    fun downFromPickFileVisitsHistoryArrow() {
        showMore()
        composeRule.onNodeWithText("Pick file").requestFocus()
        press(Key.DirectionDown)
        composeRule.onNodeWithContentDescription("History").assertIsFocused()
    }

    @Test
    fun upFromVideoVisitsHistoryArrowAndDownRestoresTheVideo() {
        showMore()
        composeRule.onNodeWithText("Clip 02").requestFocus()
        press(Key.DirectionUp)
        composeRule.onNodeWithContentDescription("History").assertIsFocused()
        press(Key.DirectionDown)
        composeRule.onNodeWithText("Clip 02").assertIsFocused()
    }

    @Test
    fun historyUpdatesDoNotStealFocusFromActions() {
        showMore(emptyList())
        composeRule.onNodeWithText("Pick file").requestFocus()
        composeRule.runOnIdle { state.value = MoreUiState(history = DataState.Success(clips.reversed())) }
        composeRule.onNodeWithText("Pick file").assertIsFocused()
    }

    @Test
    fun upFromHistoryArrowReturnsToTopActions() {
        showMore()
        composeRule.onNodeWithContentDescription("History").requestFocus()
        press(Key.DirectionUp)
        composeRule.onNode(
            isFocused() and (hasText("Vault") or hasText("Pick file") or hasText("Trash")),
        ).assertExists()
    }

    @Test
    fun tabVisitsHistoryArrowBetweenActionsAndVideos() {
        showMore()
        press(Key.Tab)
        composeRule.onNodeWithText("Pick file").assertIsFocused()
        press(Key.Tab)
        composeRule.onNodeWithText("Trash").assertIsFocused()
        press(Key.Tab)
        composeRule.onNodeWithContentDescription("History").assertIsFocused()
        press(Key.Tab)
        composeRule.onNodeWithText("Clip 01").assertIsFocused()
    }

    private fun showMore(history: List<Video> = clips) {
        state.value = MoreUiState(history = DataState.Success(history))
        composeRule.setContent {
            NextPlayerTheme { MoreScreenContent(uiState = state.value, onAction = {}) }
        }
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
    }
}

private val clips = (1..10).map { index ->
    Video.sample.copy(
        id = index.toLong(),
        nameWithExtension = "Clip %02d.mp4".format(index),
        uriString = "content://video/$index",
    )
}
