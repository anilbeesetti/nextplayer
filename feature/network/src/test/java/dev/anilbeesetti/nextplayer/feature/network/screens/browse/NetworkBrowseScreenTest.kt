package dev.anilbeesetti.nextplayer.feature.network.screens.browse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
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
class NetworkBrowseScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `done applies layout and supported sort options together`() {
        val actions = showScreen()
        composeRule.onNodeWithContentDescription("Quick Settings").performClick()
        composeRule.onNodeWithText("Duration").assertDoesNotExist()
        composeRule.onNodeWithText("Location").assertDoesNotExist()
        composeRule.onNodeWithText("Grid").performClick()
        composeRule.onNodeWithContentDescription("Size").performClick()
        composeRule.onNodeWithText("Largest").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(
            listOf(NetworkBrowseAction.UpdateQuickSettings(MediaLayoutMode.GRID, Sort(Sort.By.SIZE, Sort.Order.DESCENDING))),
            actions,
        )
    }

    @Test
    fun `cancel discards pending layout and sort changes`() {
        val actions = showScreen()
        composeRule.onNodeWithContentDescription("Quick Settings").performClick()
        composeRule.onNodeWithText("Grid").performClick()
        composeRule.onNodeWithContentDescription("Date").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithContentDescription("Quick Settings").performClick()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(
            listOf(NetworkBrowseAction.UpdateQuickSettings(MediaLayoutMode.LIST, Sort(Sort.By.TITLE, Sort.Order.ASCENDING))),
            actions,
        )
    }

    @Test
    fun `grid items navigate folders and play videos`() {
        val actions = showScreen(MediaLayoutMode.GRID)
        composeRule.onNodeWithText("Folder").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Movie.mp4").assertIsDisplayed().performClick()
        assertEquals(listOf(NetworkBrowseAction.OpenFolder(folder), NetworkBrowseAction.PlayVideo(video)), actions)
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun `grid fits more folder tiles than video tiles per row`() {
        val folders = (1..4).map { NetworkFile("Folder $it", "folder$it", true) }
        val videos = (1..2).map { NetworkFile("Movie $it.mp4", "movie$it.mp4", false) }
        showScreen(MediaLayoutMode.GRID, folders + videos)

        val folderBounds = folders.map {
            composeRule.onNode(hasText(it.name) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        }
        val videoBounds = videos.map {
            composeRule.onNode(hasText(it.name) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        }
        assertTrue(folderBounds.first().width < videoBounds.first().width)
        assertEquals(1, folderBounds.map { it.top }.distinct().size)
        assertEquals(1, videoBounds.map { it.top }.distinct().size)
        assertTrue(videoBounds.first().top > folderBounds.first().bottom)
    }

    private fun showScreen(
        layout: MediaLayoutMode = MediaLayoutMode.LIST,
        files: List<NetworkFile> = listOf(folder, video),
    ): MutableList<NetworkBrowseAction> {
        val actions = mutableListOf<NetworkBrowseAction>()
        composeRule.setContent {
            NextPlayerTheme {
                NetworkBrowseScreenContent(
                    state = NetworkBrowseUiState(
                        title = "WebDAV",
                        files = files,
                        isLoading = false,
                        preferences = ApplicationPreferences(networkMediaLayoutMode = layout),
                    ),
                    onAction = actions::add,
                )
            }
        }
        return actions
    }

    private val folder = NetworkFile("Folder", "folder", true)
    private val video = NetworkFile("Movie.mp4", "movie.mp4", false)
}
