package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.domain.MediaHolder
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MediaPickerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingWithoutStoragePermissionShowsPermissionMessage() {
        val permissionMessage = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.permission_not_granted)

        composeRule.setContent {
            NextPlayerTheme {
                MediaPickerScreenContent(
                    state = MediaPickerUiState(folderName = null),
                )
            }
        }

        composeRule.onNodeWithText(permissionMessage).assertIsDisplayed()
    }

    @Test
    fun grantingStoragePermissionFromSettingsStartsMediaCollection() {
        val actions = mutableListOf<MediaPickerAction>()
        composeRule.setContent {
            NextPlayerTheme {
                MediaPickerScreenContent(
                    state = MediaPickerUiState(folderName = null),
                    onAction = actions::add,
                )
            }
        }
        composeRule.waitForIdle()

        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertTrue(MediaPickerAction.OnPermissionAccepted in actions)
    }

    @Test
    fun choosingPlaylistDispatchesActionAndExitsSelectionMode() {
        val actions = mutableListOf<MediaPickerAction>()
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        var showPlaylistDialog by mutableStateOf(false)
        val video = Video.sample.copy(nameWithExtension = "Video.mp4", uriString = "content://video/1")
        composeRule.setContent {
            NextPlayerTheme {
                MediaPickerScreenContent(
                    state = MediaPickerUiState(
                        folderName = null,
                        mediaDataState = DataState.Success(MediaHolder(folders = emptyList(), videos = listOf(video))),
                        playlists = listOf(
                            PlaylistSummary(
                                id = 7,
                                name = "Movies",
                                type = PlaylistType.LOCAL,
                                itemCount = 2,
                                lastRefreshedAt = null,
                            ),
                        ),
                        addToPlaylistState = AddToPlaylistState(
                            isVisible = showPlaylistDialog,
                            hasVideos = true,
                        ),
                    ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Video").performTouchInput { longClick() }
        composeRule.onNodeWithText("1 / 1 Selected").assertIsDisplayed()
        composeRule.runOnIdle { showPlaylistDialog = true }

        composeRule.onNodeWithText("Choose a playlist").assertIsDisplayed()
        composeRule.onNodeWithText("Create new playlist").assertIsDisplayed()
        composeRule.onNodeWithText("Movies").performClick()

        assertTrue(MediaPickerAction.AddSelectionToPlaylist(7) in actions)
        composeRule.runOnIdle { showPlaylistDialog = false }
        composeRule.onNodeWithText("1 / 1 Selected").assertDoesNotExist()
    }
}
