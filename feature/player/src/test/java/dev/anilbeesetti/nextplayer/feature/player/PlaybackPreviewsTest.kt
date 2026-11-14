package dev.anilbeesetti.nextplayer.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackPreviewsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun everyCaseRendersBothSurfacesWithItsConnectionActions() {
        var case by mutableStateOf(playbackPreviewCases.first())
        var landscape by mutableStateOf(false)
        rule.setContent {
            Column {
                MiniPlayerPreviewContent(case)
                ExpandedPlaybackPreviewContent(
                    case,
                    landscape,
                )
            }
        }
        for (previewCase in playbackPreviewCases) {
            for (isLandscape in listOf(
                false,
                true,
            )) {
                rule.runOnIdle {
                    case = previewCase
                    landscape = isLandscape
                }
                rule.onNodeWithTag("mini_player").assertExists()
                rule.onNodeWithTag("expanded_player").assertExists()
                assertActionCount(
                    R.string.cancel,
                    previewCase.connection == PlaybackConnection.CONNECTING,
                )
                assertActionCount(
                    R.string.retry,
                    previewCase.connection == PlaybackConnection.LOST,
                )
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun longPortraitTitleStaysOnOneLine() {
        rule.setContent {
            ExpandedPlaybackPreviewContent(
                playbackPreviewCases.first { it.name == "Long text" },
                landscape = false,
            )
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("playback_media_title")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(
            1,
            layouts.single().lineCount,
        )
        assertTrue(layouts.single().isLineEllipsized(0))
    }

    private fun assertActionCount(label: Int, shown: Boolean) {
        assertEquals(
            if (shown) 1 else 0,
            rule.onAllNodesWithText(rule.activity.getString(label)).fetchSemanticsNodes().size,
        )
    }
}
