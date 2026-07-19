package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.ui.R
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
}
