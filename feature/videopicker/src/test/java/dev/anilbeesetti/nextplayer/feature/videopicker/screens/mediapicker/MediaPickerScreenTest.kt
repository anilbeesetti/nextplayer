package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionManager
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
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun tvFolderFabUpReturnsToTheVideoInsteadOfSettings() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        val video = Video.sample.copy(nameWithExtension = "Clip.mp4", uriString = "content://video/1")
        composeRule.setContent {
            NextPlayerTheme {
                MediaPickerScreen(
                    uiState = MediaPickerUiState(
                        folderName = "Movies",
                        mediaDataState = DataState.Success(MediaHolder(listOf(video), emptyList())),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Clip").requestFocus()
        composeRule.onNodeWithContentDescription("Play").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText("Clip").assertIsFocused()
    }

    @Test
    fun loadingWithoutStoragePermissionShowsPermissionMessage() {
        val permissionMessage = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.permission_not_granted)

        composeRule.setContent {
            NextPlayerTheme {
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
            NextPlayerTheme {
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
            NextPlayerTheme {
                MediaPickerScreen(
                    uiState = MediaPickerUiState(
                        folderName = null,
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
