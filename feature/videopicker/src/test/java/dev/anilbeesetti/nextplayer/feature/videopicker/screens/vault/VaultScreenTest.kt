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
        composeRule.onNodeWithContentDescription(label = "Quick Settings").performClick()
        composeRule.onNodeWithText(text = "Location").assertDoesNotExist()
        composeRule.onNodeWithText(text = "Grid").performClick()
        composeRule.onNodeWithContentDescription(label = "Date hidden").performClick()
        composeRule.onNodeWithText(
            text = "Newest",
        ).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithText(text = "Done").performClick()
        assertEquals(
            listOf(
                VaultAction.UpdateQuickSettings(
                    MediaLayoutMode.GRID,
                    sort = Sort(
                        Sort.By.DATE,
                        Sort.Order.DESCENDING,
                    ),
                ),
            ),
            actions,
        )
    }

    @Test
    fun `cancel does not change vault settings`() {
        val actions = showScreen()
        composeRule.onNodeWithContentDescription(label = "Quick Settings").performClick()
        composeRule.onNodeWithText(text = "Grid").performClick()
        composeRule.onNodeWithContentDescription(label = "Duration").performClick()
        composeRule.onNodeWithText(text = "Cancel").performClick()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithContentDescription(label = "Quick Settings").performClick()
        composeRule.onNodeWithText(text = "Done").performClick()
        assertEquals(
            listOf(
                VaultAction.UpdateQuickSettings(
                    MediaLayoutMode.LIST,
                    sort = Sort(
                        Sort.By.TITLE,
                        Sort.Order.ASCENDING,
                    ),
                ),
            ),
            actions,
        )
    }

    @Test
    fun `locked vault does not expose quick settings`() {
        composeRule.setContent {
            NextPlayerTheme {
                VaultScreenContent(
                    state = VaultUiState(stage = VaultStage.LOCKED),
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(label = "Quick Settings").assertDoesNotExist()
    }

    private fun showScreen(): MutableList<VaultAction> {
        val actions = mutableListOf<VaultAction>()
        composeRule.setContent {
            NextPlayerTheme {
                VaultScreenContent(
                    state = VaultUiState(
                        stage = VaultStage.UNLOCKED,
                        sort = Sort(
                            Sort.By.TITLE,
                            Sort.Order.ASCENDING,
                        ),
                    ),
                    onAction = actions::add,
                )
            }
        }
        return actions
    }
}
