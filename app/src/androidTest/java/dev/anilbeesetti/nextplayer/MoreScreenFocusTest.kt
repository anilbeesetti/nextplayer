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

    private val state =
        mutableStateOf(value = MoreUiState(history = DataState.Success(value = clips)))

    @Before
    fun requireTv() {
        assumeTrue(composeRule.activity.isTelevision)
    }

    @Test
    fun enteringMoreFocusesVault() {
        showMore()
        composeRule.onNodeWithText(text = "Vault").assertIsFocused()
    }

    @Test
    fun emptyHistoryKeepsTheFirstActionFocused() {
        showMore(history = emptyList())
        composeRule.onNodeWithText(text = "Vault").assertIsFocused()
        composeRule.onNodeWithContentDescription(label = "History").assertExists()
    }

    @Test
    fun downFromTrashVisitsHistoryArrowBeforeVideos() {
        showMore()
        composeRule.onNodeWithText(text = "Trash").requestFocus()
        press(Key.DirectionDown)
        composeRule.onNodeWithContentDescription(label = "History").assertIsFocused()
        press(Key.DirectionDown)
        composeRule.onNodeWithText(text = "Clip 01").assertIsFocused()
    }

    @Test
    fun downFromPickFileVisitsHistoryArrow() {
        showMore()
        composeRule.onNodeWithText(text = "Pick file").requestFocus()
        press(Key.DirectionDown)
        composeRule.onNodeWithContentDescription(label = "History").assertIsFocused()
    }

    @Test
    fun upFromVideoVisitsHistoryArrowAndDownRestoresTheVideo() {
        showMore()
        composeRule.onNodeWithText(text = "Clip 02").requestFocus()
        press(Key.DirectionUp)
        composeRule.onNodeWithContentDescription(label = "History").assertIsFocused()
        press(Key.DirectionDown)
        composeRule.onNodeWithText(text = "Clip 02").assertIsFocused()
    }

    @Test
    fun historyUpdatesDoNotStealFocusFromActions() {
        showMore(history = emptyList())
        composeRule.onNodeWithText(text = "Pick file").requestFocus()
        composeRule.runOnIdle {
            state.value =
                MoreUiState(history = DataState.Success(clips.reversed()))
        }
        composeRule.onNodeWithText(text = "Pick file").assertIsFocused()
    }

    @Test
    fun upFromHistoryArrowReturnsToTopActions() {
        showMore()
        composeRule.onNodeWithContentDescription(label = "History").requestFocus()
        press(Key.DirectionUp)
        composeRule.onNode(
            matcher =
                isFocused() and
                    (
                        hasText(text = "Vault") or hasText(text = "Pick file") or
                            hasText(text = "Trash")
                        ),
        ).assertExists()
    }

    @Test
    fun tabVisitsHistoryArrowBetweenActionsAndVideos() {
        showMore()
        press(Key.Tab)
        composeRule.onNodeWithText(text = "Pick file").assertIsFocused()
        press(Key.Tab)
        composeRule.onNodeWithText(text = "Trash").assertIsFocused()
        press(Key.Tab)
        composeRule.onNodeWithContentDescription(label = "History").assertIsFocused()
        press(Key.Tab)
        composeRule.onNodeWithText(text = "Clip 01").assertIsFocused()
    }

    private fun showMore(history: List<Video> = clips) {
        state.value = MoreUiState(history = DataState.Success(value = history))
        composeRule.setContent {
            NextPlayerTheme {
                MoreScreenContent(
                    state = state.value,
                    onAction = {},
                )
            }
        }
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key = key) }
    }
}

private val clips = (1..10).map { index ->
    Video.sample.copy(
        id = index.toLong(),
        nameWithExtension = "Clip %02d.mp4".format(index),
        uriString = "content://video/$index",
    )
}
