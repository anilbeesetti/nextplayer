package dev.anilbeesetti.nextplayer.feature.videopicker

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.anilbeesetti.nextplayer.core.data.models.Folder
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.CIRCULAR_PROGRESS_INDICATOR_TEST_TAG
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.MediaPickerScreen
import org.junit.Rule
import org.junit.Test

class MediaPickerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * This test is to check if the CircularProgressIndicator is displayed when the [MediaState.Loading] is passed.
     */
    @Test
    fun circularProgressIndicatorIsDisplayed_whenLoading() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    mediaState = MediaState.Loading,
                    preferences = AppPreferences()
                )
            }
        }

        composeTestRule.onNodeWithTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG).assertExists()
    }

    /**
     * This test is to check if the video items are displayed
     * when the [MediaState.Success] is passed with list of [Video],
     * along with [AppPreferences].groupVideosByFolder = false
     */
    @Test
    fun videoItemsAreDisplayed_whenSuccessAndGroupVideosByFolderIsFalse() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    mediaState = MediaState.Success(
                        data = videoItemsTestData
                    ),
                    preferences = AppPreferences().copy(groupVideosByFolder = false)
                )
            }
        }

        composeTestRule
            .onNodeWithText(
                videoItemsTestData[0].displayName,
                substring = true
            )
            .assertExists()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithText(
                videoItemsTestData[1].displayName,
                substring = true
            )
            .assertExists()
            .assertHasClickAction()
    }

    /**
     * This test is to check if the folder items are displayed
     * when the [MediaState.Success] is passed with list of [Folder],
     * along with [AppPreferences].groupVideosByFolder = true
     */
    @Test
    fun folderItemsAreDisplayed_whenSuccessAndGroupVideosByFolderIsTrue() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    mediaState = MediaState.Success(
                        data = foldersTestData
                    ),
                    preferences = AppPreferences().copy(groupVideosByFolder = true)
                )
            }
        }

        composeTestRule
            .onNodeWithText(
                foldersTestData[0].name,
                substring = true
            )
            .assertExists()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithText(
                foldersTestData[1].name,
                substring = true
            )
            .assertExists()
            .assertHasClickAction()
    }

    /**
     * This test is to check if the no videos found text is displayed,
     * when the [MediaState.Success] with empty list is passed,
     * along with [AppPreferences].groupVideosByFolder = false
     */
    @Test
    fun noVideosFoundTextIsDisplayed_whenSuccessWithEmptyListAndGroupVideosByFolderIsFalse() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    mediaState = MediaState.Success(
                        data = emptyList<Video>()
                    ),
                    preferences = AppPreferences().copy(groupVideosByFolder = false)
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
     * when the [MediaState.Success] with empty list is passed,
     * along with [AppPreferences].groupVideosByFolder = true
     */
    @Test
    fun noVideosFoundTextIsDisplayed_whenSuccessWithEmptyListAndGroupVideosByFolderIsTrue() {
        composeTestRule.setContent {
            BoxWithConstraints {
                MediaPickerScreen(
                    mediaState = MediaState.Success(
                        data = emptyList<Folder>()
                    ),
                    preferences = AppPreferences().copy(groupVideosByFolder = true)
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
        nameWithExtension = "Video 1.mp4"
    ),
    Video(
        id = 2,
        path = "/storage/emulated/0/DCIM/Camera/Video 2.mp4",
        displayName = "Video 2",
        uriString = "",
        duration = 2000,
        width = 200,
        height = 200,
        nameWithExtension = "Video 2.mp4"
    )
)

val foldersTestData = listOf(
    Folder(
        name = "Folder 1",
        path = "/storage/emulated/0/DCIM/Camera/Folder 1",
        mediaCount = 1,
        mediaSize = 1000
    ),
    Folder(
        name = "Folder 2",
        path = "/storage/emulated/0/DCIM/Camera/Folder 2",
        mediaCount = 2,
        mediaSize = 2000
    )
)
