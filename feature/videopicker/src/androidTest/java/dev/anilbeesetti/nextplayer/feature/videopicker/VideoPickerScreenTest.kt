package dev.anilbeesetti.nextplayer.feature.videopicker

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import org.junit.Rule
import org.junit.Test

class VideoPickerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * This test is to check if the CircularProgressIndicator is displayed when the [VideosState.Loading] is passed.
     */
    @Test
    fun circularProgressIndicatorIsDisplayed_whenLoading() {
        composeTestRule.setContent {
            BoxWithConstraints {
                VideoPickerScreen(
                    title = "Title",
                    videosState = VideosState.Loading,
                    preferences = AppPreferences()
                )
            }
        }

        composeTestRule.onNodeWithTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG).assertExists()
    }

    /**
     * This test is to check if the video items are displayed when the [VideosState.Success] is passed.
     */
    @Test
    fun videoItemsAreDisplayed_whenSuccess() {
        composeTestRule.setContent {
            BoxWithConstraints {
                VideoPickerScreen(
                    title = "Title",
                    videosState = VideosState.Success(
                        videos = videoItemsTestData
                    ),
                    preferences = AppPreferences()
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
     * This test is to check if the no videos found text is displayed,
     * when the [VideosState.Success] with empty list is passed.
     */
    @Test
    fun noVideosFoundTextIsDisplayed_whenSuccessWithEmptyList() {
        composeTestRule.setContent {
            BoxWithConstraints {
                VideoPickerScreen(
                    title = "Title",
                    videosState = VideosState.Success(
                        videos = emptyList()
                    ),
                    preferences = AppPreferences()
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
