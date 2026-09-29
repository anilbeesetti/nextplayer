package dev.anilbeesetti.nextplayer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.ControlsTopView
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoGridItem
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaTitleLineBreakTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val title = "悬案.Unsettled.Case.S01E02.2026.2160p.WEB-DL.H265"
    private val video = Video.sample.copy(nameWithExtension = "$title.mp4")
    private val preferences = ApplicationPreferences(showExtensionField = false, showThumbnailField = false)

    @Test
    fun listTitleDoesNotIsolateChinesePrefix() = assertTitleWraps {
        VideoListItem(
            modifier = Modifier.requiredWidth(360.dp),
            video = video,
            isRecentlyPlayedVideo = false,
            preferences = preferences,
        )
    }

    @Test
    fun gridTitleDoesNotIsolateChinesePrefix() = assertTitleWraps {
        VideoGridItem(
            modifier = Modifier.requiredWidth(180.dp),
            video = video,
            isRecentlyPlayedVideo = false,
            preferences = preferences,
        )
    }

    @Test
    fun playerTitleDoesNotIsolateChinesePrefix() = assertTitleWraps {
        ControlsTopView(
            modifier = Modifier.requiredWidth(700.dp),
            title = title,
            videoDecoderMode = null,
            onBackClick = {},
        )
    }

    private fun assertTitleWraps(content: @Composable () -> Unit) {
        composeRule.setContent { NextPlayerTheme { content() } }
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals(2, layout.lineCount)
        assertTrue("First line ends at ${layout.getLineEnd(0)}", layout.getLineEnd(0) > 2)
        assertEquals(title, layout.layoutInput.text.text)
    }
}
