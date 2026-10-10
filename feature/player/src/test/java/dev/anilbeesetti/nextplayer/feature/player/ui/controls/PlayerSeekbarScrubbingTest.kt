package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.LocalUseMaterialYouControls
import dev.anilbeesetti.nextplayer.feature.player.extensions.setIsScrubbingModeEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerSeekbarScrubbingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun losingFocusBeforeSeekKeyUpResumesPlaybackForBothStyles() {
        withSeekbar { player, sliderFocus, otherFocus, setMaterial, _ ->
            val slider = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            for (material in listOf(false, true)) {
                composeRule.runOnIdle {
                    setMaterial(material)
                }
                composeRule.runOnIdle { sliderFocus.requestFocus() }
                slider.performKeyInput { keyDown(Key.DirectionRight) }
                composeRule.runOnIdle {
                    assertTrue(player.isScrubbingModeEnabled)
                    assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_SCRUBBING, player.playbackSuppressionReason)
                    otherFocus.requestFocus()
                }
                composeRule.runOnIdle {
                    assertFalse(player.isScrubbingModeEnabled)
                    assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, player.playbackSuppressionReason)
                    assertTrue(player.playWhenReady)
                }
                // Key up now reaches the other control, not the slider.
                composeRule.onNode(isFocused()).performKeyInput { keyUp(Key.DirectionRight) }
            }
        }
    }

    @Test
    fun normalKeyReleasePreservesPausedPlaybackForBothStyles() {
        withSeekbar { player, sliderFocus, _, setMaterial, _ ->
            val slider = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            for (material in listOf(false, true)) {
                composeRule.runOnIdle {
                    player.pause()
                    setMaterial(material)
                }
                composeRule.runOnIdle { sliderFocus.requestFocus() }
                slider.performKeyInput { keyDown(Key.DirectionRight) }
                composeRule.runOnIdle { assertTrue(player.isScrubbingModeEnabled) }
                slider.performKeyInput { keyUp(Key.DirectionRight) }
                composeRule.runOnIdle {
                    assertFalse(player.isScrubbingModeEnabled)
                    assertFalse(player.playWhenReady)
                }
            }
        }
    }

    @Test
    fun removingSeekbarDuringTouchScrubResumesPlaybackForBothStyles() {
        withSeekbar { player, _, _, setMaterial, setVisible ->
            val slider = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            for (material in listOf(false, true)) {
                composeRule.runOnIdle {
                    setMaterial(material)
                    setVisible(true)
                }
                slider.performTouchInput {
                    down(Offset(width * 0.2f, centerY))
                    moveTo(Offset(width * 0.7f, centerY))
                }
                composeRule.runOnIdle {
                    assertTrue(player.isScrubbingModeEnabled)
                    setVisible(false)
                }
                composeRule.runOnIdle {
                    assertFalse(player.isScrubbingModeEnabled)
                    assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, player.playbackSuppressionReason)
                    assertTrue(player.playWhenReady)
                }
                composeRule.onRoot().performTouchInput { cancel() }
            }
        }
    }

    private fun withSeekbar(
        check: (ExoPlayer, FocusRequester, FocusRequester, (Boolean) -> Unit, (Boolean) -> Unit) -> Unit,
    ) {
        val player = composeRule.runOnIdle { ExoPlayer.Builder(composeRule.activity).build().apply { play() } }
        val sliderFocus = FocusRequester()
        val otherFocus = FocusRequester()
        var position by mutableFloatStateOf(100f)
        var material by mutableStateOf(false)
        var visible by mutableStateOf(true)
        try {
            composeRule.setContent {
                NextPlayerTheme {
                    CompositionLocalProvider(LocalUseMaterialYouControls provides material) {
                        Column {
                            if (visible) {
                                PlayerSeekbar(
                                    modifier = Modifier.focusRequester(sliderFocus),
                                    position = position,
                                    duration = 1_000f,
                                    chapters = emptyList(),
                                    onSeek = {
                                        position = it
                                        player.setIsScrubbingModeEnabled(true)
                                    },
                                    onSeekFinished = { player.setIsScrubbingModeEnabled(false) },
                                )
                            }
                            Button(modifier = Modifier.focusRequester(otherFocus), onClick = {}) { Text("Other control") }
                        }
                    }
                }
            }
            check(player, sliderFocus, otherFocus, { material = it }, { visible = it })
        } finally {
            composeRule.runOnIdle {
                visible = false
            }
            composeRule.runOnIdle { player.release() }
        }
    }
}
