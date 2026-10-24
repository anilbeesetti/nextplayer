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
        composeRule.onNodeWithContentDescription(label = "Quick Settings").performClick()
        composeRule.onNodeWithText(text = "Duration").assertDoesNotExist()
        composeRule.onNodeWithText(text = "Location").assertDoesNotExist()
        composeRule.onNodeWithText(text = "Grid").performClick()
        composeRule.onNodeWithContentDescription(label = "Size").performClick()
        composeRule.onNodeWithText(
            text = "Largest",
        ).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithText(text = "Done").performClick()
        assertEquals(
            listOf(
                NetworkBrowseAction.UpdateQuickSettings(
                    MediaLayoutMode.GRID,
                    sort = Sort(
                        Sort.By.SIZE,
                        Sort.Order.DESCENDING,
                    ),
                ),
            ),
            actions,
        )
    }

    @Test
    fun `cancel discards pending layout and sort changes`() {
        val actions = showScreen()
        composeRule.onNodeWithContentDescription(label = "Quick Settings").performClick()
        composeRule.onNodeWithText(text = "Grid").performClick()
        composeRule.onNodeWithContentDescription(label = "Date").performClick()
        composeRule.onNodeWithText(text = "Cancel").performClick()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithContentDescription(label = "Quick Settings").performClick()
        composeRule.onNodeWithText(text = "Done").performClick()
        assertEquals(
            listOf(
                NetworkBrowseAction.UpdateQuickSettings(
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
    fun `grid items navigate folders and play videos`() {
        val actions = showScreen(MediaLayoutMode.GRID)
        composeRule.onNodeWithText(text = "Folder").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(text = "Movie.mp4").assertIsDisplayed().performClick()
        assertEquals(
            listOf(
                NetworkBrowseAction.OpenFolder(file = folder),
                NetworkBrowseAction.PlayVideo(file = video),
            ),
            actions,
        )
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun `grid fits more folder tiles than video tiles per row`() {
        val folders = (1..4).map {
            NetworkFile(
                name = "Folder $it",
                path = "folder$it",
                isDirectory = true,
            )
        }
        val videos = (1..2).map {
            NetworkFile(
                name = "Movie $it.mp4",
                path = "movie$it.mp4",
                isDirectory = false,
            )
        }
        showScreen(
            MediaLayoutMode.GRID,
            files = folders + videos,
        )

        val folderBounds = folders.map {
            composeRule.onNode(
                matcher = hasText(it.name) and hasClickAction(),
            ).fetchSemanticsNode().boundsInRoot
        }
        val videoBounds = videos.map {
            composeRule.onNode(
                matcher = hasText(it.name) and hasClickAction(),
            ).fetchSemanticsNode().boundsInRoot
        }
        assertTrue(folderBounds.first().width < videoBounds.first().width)
        assertEquals(
            1,
            folderBounds.map { it.top }.distinct().size,
        )
        assertEquals(
            1,
            videoBounds.map { it.top }.distinct().size,
        )
        assertTrue(videoBounds.first().top > folderBounds.first().bottom)
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun `video section starts below an incomplete folder grid row`() {
        val secondVideo = NetworkFile(
            name = "Another.mp4",
            path = "another.mp4",
            isDirectory = false,
        )
        showScreen(
            MediaLayoutMode.GRID,
            files = listOf(
                folder,
                video,
                secondVideo,
            ),
        )

        val folderHeader = composeRule.onNodeWithText(
            text = "Folders (1)",
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val videoHeader = composeRule.onNodeWithText(
            text = "Videos (2)",
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val folderBounds = composeRule.onNode(
            matcher = hasText(folder.name) and hasClickAction(),
        ).fetchSemanticsNode().boundsInRoot
        val videoBounds = composeRule.onNode(
            matcher = hasText(video.name) and hasClickAction(),
        ).fetchSemanticsNode().boundsInRoot
        val secondVideoBounds = composeRule.onNode(
            matcher = hasText(secondVideo.name) and hasClickAction(),
        ).fetchSemanticsNode().boundsInRoot

        assertTrue(folderHeader.bottom <= folderBounds.top)
        assertTrue(folderBounds.bottom < videoHeader.top)
        assertTrue(videoHeader.bottom <= videoBounds.top)
        assertEquals(
            videoBounds.top,
            secondVideoBounds.top,
        )
    }

    @Test
    fun `list layout also shows counted folder and video sections`() {
        showScreen()

        val folderHeader = composeRule.onNodeWithText(
            text = "Folders (1)",
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val videoHeader = composeRule.onNodeWithText(
            text = "Videos (1)",
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val folderBounds = composeRule.onNode(
            matcher = hasText(folder.name) and hasClickAction(),
        ).fetchSemanticsNode().boundsInRoot
        val videoBounds = composeRule.onNode(
            matcher = hasText(video.name) and hasClickAction(),
        ).fetchSemanticsNode().boundsInRoot

        assertTrue(folderHeader.bottom <= folderBounds.top)
        assertTrue(folderBounds.bottom < videoHeader.top)
        assertTrue(videoHeader.bottom <= videoBounds.top)
    }

    @Test
    fun `folders only shows its count without an empty video section`() {
        showScreen(
            MediaLayoutMode.GRID,
            files = listOf(element = folder),
        )

        composeRule.onNodeWithText(text = "Folders (1)").assertIsDisplayed()
        composeRule.onNodeWithText(text = "Videos (0)").assertDoesNotExist()
    }

    @Test
    fun `videos only shows its count without an empty folder section`() {
        showScreen(
            MediaLayoutMode.GRID,
            files = listOf(element = video),
        )

        composeRule.onNodeWithText(text = "Videos (1)").assertIsDisplayed()
        composeRule.onNodeWithText(text = "Folders (0)").assertDoesNotExist()
    }

    @Test
    fun `empty folder keeps the empty state without section headings`() {
        showScreen(files = emptyList())

        composeRule.onNodeWithText(text = "This folder is empty").assertIsDisplayed()
        composeRule.onNodeWithText(text = "Folders (0)").assertDoesNotExist()
        composeRule.onNodeWithText(text = "Videos (0)").assertDoesNotExist()
    }

    private fun showScreen(
        layout: MediaLayoutMode = MediaLayoutMode.LIST,
        files: List<NetworkFile> = listOf(
            folder,
            video,
        ),
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

    private val folder = NetworkFile(
        name = "Folder",
        path = "folder",
        isDirectory = true,
    )
    private val video = NetworkFile(
        name = "Movie.mp4",
        path = "movie.mp4",
        isDirectory = false,
    )
}
