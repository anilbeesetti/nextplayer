package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `quick settings apply grid and vault specific sort choices`() {
        val actions = showScreen()
        composeRule.onNodeWithContentDescription("Quick Settings").performClick()
        composeRule.onNodeWithText("Location").assertDoesNotExist()
        composeRule.onNodeWithText("Grid").performClick()
        composeRule.onNodeWithContentDescription("Date hidden").performClick()
        composeRule.onNodeWithText("Newest").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(
            listOf(VaultAction.UpdateQuickSettings(MediaLayoutMode.GRID, Sort(Sort.By.DATE, Sort.Order.DESCENDING))),
            actions,
        )
    }

    @Test
    fun `cancel does not change vault settings`() {
        val actions = showScreen()
        composeRule.onNodeWithContentDescription("Quick Settings").performClick()
        composeRule.onNodeWithText("Grid").performClick()
        composeRule.onNodeWithContentDescription("Duration").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithContentDescription("Quick Settings").performClick()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(
            listOf(VaultAction.UpdateQuickSettings(MediaLayoutMode.LIST, Sort(Sort.By.TITLE, Sort.Order.ASCENDING))),
            actions,
        )
    }

    @Test
    fun `locked vault does not expose quick settings`() {
        composeRule.setContent {
            NextPlayerTheme {
                VaultScreenContent(state = VaultUiState(stage = VaultStage.LOCKED), onAction = {})
            }
        }
        composeRule.onNodeWithContentDescription("Quick Settings").assertDoesNotExist()
    }

    private fun showScreen(): MutableList<VaultAction> {
        val actions = mutableListOf<VaultAction>()
        composeRule.setContent {
            NextPlayerTheme {
                VaultScreenContent(
                    state = VaultUiState(stage = VaultStage.UNLOCKED, sort = Sort(Sort.By.TITLE, Sort.Order.ASCENDING)),
                    onAction = actions::add,
                )
            }
        }
        return actions
    }
}
