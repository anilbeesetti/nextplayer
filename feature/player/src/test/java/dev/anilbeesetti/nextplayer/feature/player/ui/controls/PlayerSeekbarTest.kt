package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.media3.extractor.metadata.Chapter
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.LocalUseMaterialYouControls
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerSeekbarTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bothStylesTickOnlyWhenScrubbingAcrossChapters() {
        val ticks = mutableListOf<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                ticks += hapticFeedbackType
            }
        }
        var position by mutableFloatStateOf(100f)
        var materialControls by mutableStateOf(false)
        var chapters by mutableStateOf(listOf(0L, 400L, 700L).map { Chapter.Builder().setStartTimeMs(it).build() })
        composeRule.setContent {
            NextPlayerTheme {
                CompositionLocalProvider(
                    LocalHapticFeedback provides haptics,
                    LocalUseMaterialYouControls provides materialControls,
                ) {
                    PlayerSeekbar(
                        position = position,
                        duration = 1_000f,
                        chapters = chapters,
                        onSeek = { position = it },
                        onSeekFinished = {},
                    )
                }
            }
        }

        val slider = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        for (material in listOf(false, true)) {
            composeRule.runOnIdle {
                materialControls = material
                position = 100f
                ticks.clear()
            }
            slider.performTouchInput {
                down(Offset(width * 0.1f, centerY))
                moveTo(Offset(width * 0.2f, centerY))
            }
            composeRule.runOnIdle { assertEquals(0, ticks.size) }
            slider.performTouchInput { moveTo(Offset(width * 0.5f, centerY)) }
            composeRule.runOnIdle { assertEquals(listOf(HapticFeedbackType.SegmentTick), ticks) }
            slider.performTouchInput { moveTo(Offset(width * 0.6f, centerY)) }
            composeRule.runOnIdle { assertEquals(1, ticks.size) }
            slider.performTouchInput {
                moveTo(Offset(width * 0.2f, centerY))
                up()
            }
            composeRule.runOnIdle {
                assertEquals(2, ticks.size)
                position = 800f
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle { assertEquals(2, ticks.size) }
            slider.performTouchInput {
                down(Offset(width * 0.8f, centerY))
                moveTo(Offset(width * 0.9f, centerY))
                up()
            }
            composeRule.runOnIdle { assertEquals(2, ticks.size) }
        }

        composeRule.runOnIdle {
            chapters = emptyList()
            ticks.clear()
        }
        slider.performTouchInput {
            down(Offset(width * 0.9f, centerY))
            moveTo(Offset(width * 0.1f, centerY))
            up()
        }
        composeRule.runOnIdle { assertEquals(0, ticks.size) }
    }
}
