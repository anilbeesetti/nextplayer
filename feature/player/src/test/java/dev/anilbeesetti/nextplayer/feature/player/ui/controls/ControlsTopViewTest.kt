package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
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
                    modifier = Modifier.focusRequester(groupFocusRequester),
                    title = "Video",
                    videoDecoderMode = null,
                    onBackClick = { backClicked = true },
                )
            }
        }
        val decoder = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.select_decoders))

        composeRule.runOnIdle { groupFocusRequester.requestFocus() }
        decoder.assertIsFocused().performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionCenter)
        }
        composeRule.runOnIdle { assertTrue(backClicked) }
        decoder.performKeyInput { pressKey(Key.DirectionRight) }
        decoder.assertIsFocused()
    }
}
