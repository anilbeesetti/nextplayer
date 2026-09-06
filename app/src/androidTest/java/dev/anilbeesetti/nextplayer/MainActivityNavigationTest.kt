package dev.anilbeesetti.nextplayer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.ui.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsOpensAfterActivityRecreation() {
        recreateHome()
        val settings = composeRule.activity.getString(R.string.settings)

        composeRule.onNodeWithContentDescription(settings).performClick()

        composeRule.onNodeWithText(settings).assertIsDisplayed()
    }

    @Test
    fun vaultOpensAfterActivityRecreation() {
        recreateHome()
        val appName = composeRule.activity.getString(R.string.app_name)
        val enterPin = composeRule.activity.getString(R.string.enter_vault_pin)
        val setPin = composeRule.activity.getString(R.string.set_vault_pin)

        composeRule.onNodeWithText(appName).performTouchInput { longClick() }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(enterPin).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(setPin).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun recreateHome() {
        val settings = composeRule.activity.getString(R.string.settings)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription(settings).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription(settings).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
