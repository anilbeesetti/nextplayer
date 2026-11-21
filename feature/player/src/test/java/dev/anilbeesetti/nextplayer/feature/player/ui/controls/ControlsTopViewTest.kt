package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ControlsTopViewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun enteringGroupFocusesDecoderWithoutTrappingNavigationToBack() {
        val groupFocusRequester = FocusRequester()
        var backClicked = false
        composeRule.setContent {
            NextPlayerTheme {
                ControlsTopView(
                    modifier = Modifier.focusRequester(focusRequester = groupFocusRequester),
                    title = "Video",
                    videoDecoderMode = null,
                    onBackClick = { backClicked = true },
                )
            }
        }
        val decoder = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.select_decoders),
        )

        composeRule.runOnIdle { groupFocusRequester.requestFocus() }
        decoder.assertIsFocused().performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionCenter)
        }
        composeRule.runOnIdle { assertTrue(backClicked) }
        decoder.performKeyInput { pressKey(Key.DirectionRight) }
        decoder.assertIsFocused()
    }

    @Test
    fun narrowWidthMovesTrackActionsIntoMoreMenu() {
        var moreClicked = false
        setTopView(
            width = 360.dp,
            onMoreClick = { moreClicked = true },
        )

        node(R.string.audio_track).assertDoesNotExist()
        node(R.string.subtitle_track).assertDoesNotExist()
        node(R.string.more).performClick()
        composeRule.runOnIdle { assertTrue(moreClicked) }
    }

    @Test
    fun wideWidthShowsAllActionsAndMoreMenu() {
        setTopView(width = 900.dp)

        node(R.string.now_playing).assertExists()
        node(R.string.audio_track).assertExists()
        node(R.string.subtitle_track).assertExists()
        node(R.string.more).assertExists()
    }

    @Test
    fun mediumWidthReservesSpaceForMoreMenu() {
        setTopView(width = 480.dp)

        node(R.string.now_playing).assertExists()
        node(R.string.audio_track).assertExists()
        node(R.string.subtitle_track).assertDoesNotExist()
        node(R.string.more).assertExists()
    }

    private fun setTopView(width: Dp, onMoreClick: () -> Unit = {}) {
        composeRule.setContent {
            NextPlayerTheme {
                ControlsTopView(
                    modifier = Modifier.requiredWidth(width),
                    title = "Video",
                    videoDecoderMode = null,
                    onMoreClick = onMoreClick,
                )
            }
        }
    }

    private fun node(stringRes: Int) =
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(stringRes))
}
