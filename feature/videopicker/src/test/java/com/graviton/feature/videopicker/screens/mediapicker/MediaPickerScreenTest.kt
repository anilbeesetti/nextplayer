package com.graviton.feature.videopicker.screens.mediapicker

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.graviton.core.ui.R
import com.graviton.core.model.PlaylistSummary
import com.graviton.core.ui.theme.GravitonTheme
import com.graviton.feature.videopicker.state.SelectionItem
import com.graviton.feature.videopicker.state.SelectionManager
import org.junit.Assert.assertFalse
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
            GravitonTheme {
                MediaPickerScreen(
                    uiState = MediaPickerUiState(folderName = null),
                )
            }
        }

        composeRule.onNodeWithText(permissionMessage).assertIsDisplayed()
    }

    @Test
    fun grantingStoragePermissionFromSettingsStartsMediaCollection() {
        val actions = mutableListOf<MediaPickerAction>()
        composeRule.setContent {
            GravitonTheme {
                MediaPickerScreen(
                    uiState = MediaPickerUiState(folderName = null),
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
        val selectionManager = SelectionManager(
            initialSelectionItems = setOf(
                SelectionItem.Video(
                    name = "Video",
                    uriString = "content://video/1",
                    path = "/storage/emulated/0/video.mp4",
                ),
            ),
            initialIsInSelectionMode = true,
        )
        composeRule.setContent {
            GravitonTheme {
                MediaPickerScreen(
                    uiState = MediaPickerUiState(
                        folderName = null,
                        playlists = listOf(PlaylistSummary(7, "Movies", 2)),
                        addToPlaylistState = AddToPlaylistState(
                            isVisible = true,
                            hasVideos = true,
                        ),
                    ),
                    selectionManager = selectionManager,
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Choose a playlist").assertIsDisplayed()
        composeRule.onNodeWithText("Create new playlist").assertIsDisplayed()
        composeRule.onNodeWithText("Movies").performClick()

        assertTrue(MediaPickerAction.AddSelectionToPlaylist(7) in actions)
        assertFalse(selectionManager.isInSelectionMode)
        assertTrue(selectionManager.selectionItems.isEmpty())
    }
}
