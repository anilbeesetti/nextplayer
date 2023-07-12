package dev.anilbeesetti.nextplayer.feature.videopicker

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Directory
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.CIRCULAR_PROGRESS_INDICATOR_TEST_TAG
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.MediaPickerScreen
import org.junit.Rule
import org.junit.Test

class MediaPickerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * This test is to check if the CircularProgressIndicator is displayed when the [VideosState.Loading] is passed.
     */
    @Test
    fun circularProgressIndicatorIsDisplayed_whenLoading() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    videosState = VideosState.Loading,
                    foldersState = FoldersState.Loading,
                    preferences = ApplicationPreferences()
                )
            }
        }

        composeTestRule.onNodeWithTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG).assertExists()
    }

    /**
     * This test is to check if the video items are displayed
     * when the [VideosState.Success] is passed with list of [Video],
     * along with [ApplicationPreferences].groupVideosByFolder = false
     */
    @Test
    fun videoItemsAreDisplayed_whenSuccessAndGroupVideosByFolderIsFalse() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    videosState = VideosState.Success(
                        data = videoItemsTestData
                    ),
                    foldersState = FoldersState.Loading,
                    preferences = ApplicationPreferences().copy(groupVideosByFolder = false)
                )
            }
        }

        composeTestRule
            .onNodeWithText(
                videoItemsTestData[0].displayName,
                substring = true
            )
            .onParent()
            .assertExists()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithText(
                videoItemsTestData[1].displayName,
                substring = true
            )
            .onParent()
            .assertExists()
            .assertHasClickAction()
    }

    /**
     * This test is to check if the folder items are displayed
     * when the [FoldersState.Success] is passed with list of [Directory],
     * along with [ApplicationPreferences].groupVideosByFolder = true
     */
    @Test
    fun folderItemsAreDisplayed_whenSuccessAndGroupVideosByFolderIsTrue() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    videosState = VideosState.Loading, // Don't care what the videos state is
                    foldersState = FoldersState.Success(
                        data = foldersTestData
                    ),
                    preferences = ApplicationPreferences().copy(groupVideosByFolder = true)
                )
            }
        }

        composeTestRule
            .onNodeWithText(
                foldersTestData[0].name,
                substring = true
            )
            .onParent()
            .assertExists()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithText(
                foldersTestData[1].name,
                substring = true
            )
            .onParent()
            .assertExists()
            .assertHasClickAction()
    }

    /**
     * This test is to check if the no videos found text is displayed,
     * when the [VideosState.Success] with empty list is passed,
     * along with [ApplicationPreferences].groupVideosByFolder = false
     */
    @Test
    fun noVideosFoundTextIsDisplayed_whenSuccessWithEmptyListAndGroupVideosByFolderIsFalse() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    videosState = VideosState.Success(
                        data = emptyList()
                    ),
                    foldersState = FoldersState.Loading,
                    preferences = ApplicationPreferences().copy(groupVideosByFolder = false)
                )
            }
        }

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.no_videos_found),
                substring = true
            )
            .assertExists()
    }

    /**
     * This test is to check if the no videos found text is displayed,
     * when the [FoldersState.Success] with empty list is passed,
     * along with [ApplicationPreferences].groupVideosByFolder = true
     */
    @Test
    fun noVideosFoundTextIsDisplayed_whenSuccessWithEmptyListAndGroupVideosByFolderIsTrue() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    videosState = VideosState.Loading,
                    foldersState = FoldersState.Success(
                        data = emptyList()
                    ),
                    preferences = ApplicationPreferences().copy(groupVideosByFolder = true)
                )
            }
        }

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.no_videos_found),
                substring = true
            )
            .assertExists()
    }
}

val videoItemsTestData = listOf(
    Video(
        id = 1,
        path = "/storage/emulated/0/DCIM/Camera/Video 1.mp4",
        displayName = "Video 1",
        uriString = "",
        duration = 1000,
        width = 100,
        height = 100,
        nameWithExtension = "Video 1.mp4",
        size = 1000
    ),
    Video(
        id = 2,
        path = "/storage/emulated/0/DCIM/Camera/Video 2.mp4",
        displayName = "Video 2",
        uriString = "",
        duration = 2000,
        width = 200,
        height = 200,
        nameWithExtension = "Video 2.mp4",
        size = 2000
    )
)

val foldersTestData = listOf(
    Directory(
        name = "Folder 1",
        path = "/storage/emulated/0/DCIM/Camera/Folder 1",
        mediaCount = 1,
        mediaSize = 1000,
        dateModified = 1000
    ),
    Directory(
        name = "Folder 2",
        path = "/storage/emulated/0/DCIM/Camera/Folder 2",
        mediaCount = 2,
        mediaSize = 2000,
        dateModified = 1000
    )
)
