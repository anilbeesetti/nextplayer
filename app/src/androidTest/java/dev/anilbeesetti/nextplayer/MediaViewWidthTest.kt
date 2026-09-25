package dev.anilbeesetti.nextplayer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.domain.MediaHolder
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaViewWidthTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun gridSurvivesNarrowAndZeroWidthThenExpands() {
        var width by mutableStateOf(360.dp)
        composeRule.setContent {
            NextPlayerTheme {
                Box(Modifier.requiredWidth(width)) {
                    MediaView(
                        mediaHolder = MediaHolder(
                            videos = listOf(Video.sample.copy(nameWithExtension = "Clip.mp4")),
                            folders = listOf(Folder.sample.copy(name = "Movies")),
                        ),
                        recentlyPlayedVideo = null,
                        recentlyPlayedFolder = null,
                        preferences = ApplicationPreferences(
                            mediaLayoutMode = MediaLayoutMode.GRID,
                            showThumbnailField = false,
                            showExtensionField = false,
                        ),
                        showHeaders = false,
                        autoFocus = false,
                        onFolderClick = {},
                        onVideoClick = {},
                    )
                }
            }
        }

        // 120dp leaves no video columns; 80dp and 0dp leave neither kind of column.
        for (nextWidth in listOf(360, 80, 120, 0, 360)) {
            composeRule.runOnIdle { width = nextWidth.dp }
            composeRule.waitForIdle()
            if (nextWidth > 0) {
                composeRule.onNodeWithText("Movies").assertIsDisplayed()
                composeRule.onNodeWithText("Clip").assertIsDisplayed()
            }
        }
    }
}
