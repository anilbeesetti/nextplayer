package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.ui.MoreOptionsView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ControlsTopViewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun enteringGroupFocusesDecoderWithoutTrappingNavigationToBack() {
        val groupFocusRequester = FocusRequester()
        var backClicked = false
        composeRule.setContent {
            NextPlayerTheme {
                ControlsTopView(
                    modifier = Modifier.focusRequester(focusRequester = groupFocusRequester),
                    title = "Video",
                    videoDecoderMode = null,
                    inlineActionCount = 0,
                    onBackClick = { backClicked = true },
                )
            }
        }
        val decoder = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.select_decoders),
        )

        composeRule.runOnIdle { groupFocusRequester.requestFocus() }
        decoder.assertIsFocused().performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionCenter)
        }
        composeRule.runOnIdle { assertTrue(backClicked) }
        decoder.performKeyInput { pressKey(Key.DirectionRight) }
        decoder.assertIsFocused()
    }

    @Test
    fun narrowWidthMovesTrackActionsIntoMoreMenu() {
        var moreClicked = false
        setTopView(
            width = 360.dp,
            onMoreClick = { moreClicked = true },
        )

        node(R.string.audio_track).assertDoesNotExist()
        node(R.string.subtitle_track).assertDoesNotExist()
        node(R.string.more).performClick()
        composeRule.runOnIdle { assertTrue(moreClicked) }
    }

    @Test
    fun wideWidthShowsAllActionsAndMoreMenu() {
        setTopView(width = 900.dp)

        node(R.string.now_playing).assertExists()
        node(R.string.audio_track).assertExists()
        node(R.string.subtitle_track).assertExists()
        node(R.string.more).assertExists()
    }

    @Test
    fun mediumWidthReservesSpaceForMoreMenu() {
        setTopView(width = 480.dp)

        node(R.string.now_playing).assertExists()
        node(R.string.audio_track).assertExists()
        node(R.string.subtitle_track).assertDoesNotExist()
        node(R.string.more).assertExists()
    }

    private fun setTopView(width: Dp, onMoreClick: () -> Unit = {}) {
        composeRule.setContent {
            NextPlayerTheme {
                BoxWithConstraints(modifier = Modifier.requiredWidth(width)) {
                    ControlsTopView(
                        title = "Video",
                        videoDecoderMode = null,
                        inlineActionCount = inlineTopActionCount(maxWidth),
                        onMoreClick = onMoreClick,
                    )
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp-port")
    fun portraitMoreShowsOnlyActionsThatDidNotFitInTopBar() {
        var showMore by mutableStateOf(false)
        var width by mutableStateOf(411.dp)
        composeRule.setContent {
            NextPlayerTheme {
                BoxWithConstraints(modifier = Modifier.requiredWidth(width)) {
                    val inlineActionCount = inlineTopActionCount(maxWidth)
                    if (!showMore) {
                        ControlsTopView(
                            title = "Video",
                            videoDecoderMode = null,
                            inlineActionCount = inlineActionCount,
                            onMoreClick = { showMore = true },
                        )
                    }
                    MoreOptionsView(
                        show = showMore,
                        inlineActionCount = inlineActionCount,
                        mediaInfo = null,
                        onOptionClick = {},
                        onShare = {},
                        onRename = {},
                        onInfo = {},
                        onDismissInfo = {},
                    )
                }
            }
        }

        node(R.string.now_playing).assertExists()
        node(R.string.more).performClick()
        node(R.string.now_playing).assertDoesNotExist()
        val audio = node(R.string.audio_track).getUnclippedBoundsInRoot()
        val subtitles = node(R.string.subtitle_track).getUnclippedBoundsInRoot()
        val share = node(R.string.share).getUnclippedBoundsInRoot()
        val rename = node(R.string.rename).getUnclippedBoundsInRoot()
        val info = node(R.string.info).getUnclippedBoundsInRoot()
        assertTrue(subtitles.left - audio.left in 79.dp..81.dp)
        assertTrue(share.left - subtitles.left in 79.dp..81.dp)
        assertTrue(rename.top == audio.top)
        assertTrue(info.top - audio.bottom >= 12.dp)

        composeRule.runOnIdle { width = 320.dp }
        val narrowAudio = node(R.string.audio_track).getUnclippedBoundsInRoot()
        val narrowShare = node(R.string.share).getUnclippedBoundsInRoot()
        assertTrue(narrowShare.top > narrowAudio.bottom)
    }

    private fun node(stringRes: Int) =
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(stringRes))
}
