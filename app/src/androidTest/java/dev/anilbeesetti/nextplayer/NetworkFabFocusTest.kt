package dev.anilbeesetti.nextplayer

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.ui.R
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkFabFocusTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun openNetwork() {
        assumeTrue(composeRule.activity.isTelevision)
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[2]
            .performClick()
        composeRule.onNodeWithText(text(R.string.example_url)).assertIsFocused()
    }

    @Test
    fun upFromFabTargetsUrlWhenStreamButtonIsDisabled() {
        composeRule.onNodeWithText(text(R.string.open_network_stream)).assertIsNotEnabled()
        upFromFab()
        composeRule.onNodeWithText(text(R.string.example_url)).assertIsFocused()
    }

    @Test
    fun upFromFabTargetsEnabledStreamButtonEvenWhenUrlWasLastFocused() {
        editUrl("https://example.invalid/video.mp4")
        composeRule.onNodeWithText(text(R.string.open_network_stream)).assertIsEnabled()
        upFromFab()
        composeRule.onNodeWithText(text(R.string.open_network_stream)).assertIsFocused()
    }

    @Test
    fun clearingUrlRetargetsFabUpToTheTextField() {
        editUrl("https://example.invalid/video.mp4")
        upFromFab()
        composeRule.onNodeWithText(text(R.string.open_network_stream)).assertIsFocused()
        composeRule.onNodeWithText("https://example.invalid/video.mp4").requestFocus()
        editUrl(" ")
        composeRule.onNodeWithText(text(R.string.open_network_stream)).assertIsNotEnabled()
        upFromFab()
        composeRule.onNodeWithText(" ").assertIsFocused()
    }

    @Test
    fun returningFromAddConnectionRestoresFabUpTarget() {
        editUrl("https://example.invalid/video.mp4")
        composeRule.onNodeWithTag("top_level_fab").performClick()
        composeRule.onNodeWithText(text(R.string.add_connection)).assertExists()
        pressBack()
        upFromFab()
        composeRule.onNodeWithText(text(R.string.open_network_stream)).assertIsFocused()
    }

    private fun editUrl(value: String) {
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNode(isFocused()).performTextReplacement(value)
        pressBack()
        composeRule.waitForIdle()
    }

    private fun upFromFab() {
        composeRule.onNodeWithTag("top_level_fab").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
    }

    private fun text(id: Int) = composeRule.activity.getString(id)
}
