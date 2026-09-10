package dev.anilbeesetti.nextplayer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabKey
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabState
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.navigation.rememberTopLevelNavState
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationLayoutFabFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun requireTv() {
        assumeTrue(composeRule.activity.isTelevision)
    }

    @Test
    fun upEntersContentEvenWhenItsFocusGroupOverlapsFab() {
        showScreen()
        upFromFab()
        composeRule.onNodeWithText("Second item").assertIsFocused()
    }

    @Test
    fun upRestoresThePreviousItemWithinContent() {
        showScreen()
        composeRule.onNodeWithText("First item").requestFocus()
        upFromFab()
        composeRule.onNodeWithText("First item").assertIsFocused()
    }

    @Test
    fun emptyContentFallsBackToTheScreenToolbar() {
        showScreen(empty = true)
        upFromFab()
        composeRule.onNodeWithText("Settings").assertIsFocused()
    }

    @Test
    fun downFromTheLastItemCanStillReachFab() {
        showScreen()
        composeRule.onNodeWithText("First item").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Second item").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("top_level_fab").assertIsFocused()
    }

    @Test
    fun leftFromContentCanStillReachNavigationRail() {
        showScreen()
        composeRule.onNodeWithText("Second item").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNode(
            isFocused() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).assertExists()
    }

    private fun showScreen(empty: Boolean = false) {
        composeRule.setContent {
            NextPlayerTheme {
                val state = rememberTopLevelNavState()
                val fabs = remember {
                    mutableStateMapOf(
                        TopLevelFabKey.MEDIA to TopLevelFabState(NextIcons.Play, {}),
                    )
                }
                NavigationLayout(state = state, fabStates = fabs, showBottomBar = true) {
                    Column(Modifier.fillMaxSize()) {
                        Button(onClick = {}) { Text("Settings") }
                        Box(Modifier.fillMaxSize()) {
                            if (!empty) {
                                Column(Modifier.fillMaxSize().focusRestorer().focusGroup()) {
                                    Button(onClick = {}) { Text("First item") }
                                    Button(onClick = {}) { Text("Second item") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun upFromFab() {
        composeRule.onNodeWithTag("top_level_fab").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
    }
}
