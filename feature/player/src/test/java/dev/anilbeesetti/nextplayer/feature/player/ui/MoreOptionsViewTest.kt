package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MoreOptionsViewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun infoReplacesTheButtonsAndBackReturnsToThem() {
        var mediaInfo by mutableStateOf<MediaInfo?>(null)
        var inlineActionCount by mutableIntStateOf(0)
        composeRule.setContent {
            NextPlayerTheme {
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) { inputModeManager.requestInputMode(InputMode.Keyboard) }
                Box(modifier = Modifier.fillMaxSize()) {
                    MoreOptionsView(
                        show = true,
                        inlineActionCount = inlineActionCount,
                        mediaInfo = mediaInfo,
                        onOptionClick = {},
                        onShare = {},
                        onRename = null,
                        onInfo = { mediaInfo = MediaInfo(video = Video.sample) },
                        onDismissInfo = { mediaInfo = null },
                    )
                }
            }
        }

        val info = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.info),
        )
        val fileName = composeRule.onNodeWithText(Video.sample.nameWithExtension)
        val menuTitle = composeRule.onNodeWithText(composeRule.activity.getString(R.string.more))
        val startX = menuTitle.fetchSemanticsNode().positionInRoot.x

        composeRule.mainClock.autoAdvance = false
        info.performClick()
        composeRule.mainClock.advanceTimeBy(96)
        assertTrue(menuTitle.fetchSemanticsNode().positionInRoot.x < startX)
        composeRule.mainClock.autoAdvance = true

        fileName.assertExists()
        info.assertDoesNotExist()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.navigate_up),
        ).performClick()
        info.assertExists()
        fileName.assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.now_playing),
        ).assertIsFocused()

        composeRule.runOnIdle { inlineActionCount = 3 }
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.now_playing),
        ).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.share),
        ).assertIsFocused()
    }
}
