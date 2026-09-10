package dev.anilbeesetti.nextplayer

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopLevelFabFocusTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Before
    fun requireTv() {
        assumeTrue(composeRule.activity.isTelevision)
    }

    @Test
    fun homeFabUpReturnsToContent() = checkFabUp(tabIndex = 0)

    @Test
    fun playlistsFabUpReturnsToContent() = checkFabUp(tabIndex = 1)

    @Test
    fun moreFabUpReturnsToContent() = checkFabUp(tabIndex = 3)

    @Test
    fun switchingTabsKeepsFabUpInTheCurrentScreen() {
        listOf(2, 0, 3, 1, 0, 2, 3).forEach(::checkFabUp)
    }

    private fun checkFabUp(tabIndex: Int) {
        composeRule.onAllNodes(tab)[tabIndex].performClick()
        composeRule.onNodeWithTag("top_level_fab").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNode(isFocused() and !tab and !hasTestTag("top_level_fab"))
            .assertExists()
    }
}
