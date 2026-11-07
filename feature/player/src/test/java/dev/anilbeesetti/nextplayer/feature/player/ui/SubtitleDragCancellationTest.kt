package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.feature.player.state.ControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.PictureInPictureState
import dev.anilbeesetti.nextplayer.feature.player.state.SeekGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.SubtitlePositionState
import dev.anilbeesetti.nextplayer.feature.player.state.TapGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomAndContentScaleState
import dev.anilbeesetti.nextplayer.feature.player.state.VolumeAndBrightnessGestureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubtitleDragCancellationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingToAuthoredSubtitlesCancelsTheActiveDragWithoutSaving() {
        val saved = mutableListOf<Float>()
        val position = SubtitlePositionState(0f) { saved += it }
        val canReposition = mutableStateOf(true)
        val controls = mock(ControlsVisibilityState::class.java)
        val taps = mock(TapGestureState::class.java)
        val pip = mock(PictureInPictureState::class.java)
        val seeking = mock(SeekGestureState::class.java)
        val zoom = mock(VideoZoomAndContentScaleState::class.java)
        val volume = mock(VolumeAndBrightnessGestureState::class.java)
        composeRule.setContent {
            Box(Modifier.size(300.dp)) {
                PlayerGestures(
                    modifier = Modifier.testTag("player"),
                    controlsVisibilityState = controls,
                    tapGestureState = taps,
                    pictureInPictureState = pip,
                    seekGestureState = seeking,
                    videoZoomAndContentScaleState = zoom,
                    volumeAndBrightnessGestureState = volume,
                    subtitlePositionState = position,
                    hasSubtitles = true,
                    canRepositionSubtitles = canReposition.value,
                )
            }
        }
        composeRule.runOnIdle {
            position.textBounds = Rect(
                0f,
                position.containerSize.height * 0.4f,
                position.containerSize.width.toFloat(),
                position.containerSize.height * 0.8f,
            )
        }
        val player = composeRule.onNodeWithTag("player")
        player.performTouchInput {
            down(center)
            moveBy(
                Offset(
                    0f,
                    -50f,
                ),
            )
        }
        composeRule.runOnIdle {
            assertTrue(position.isDragging)
            assertTrue(position.offsetY != 0f)
            canReposition.value = false
        }
        composeRule.waitForIdle()
        player.performTouchInput { up() }
        composeRule.runOnIdle {
            assertFalse(position.isDragging)
            assertFalse(position.isHighlighted)
            assertEquals(
                0f,
                position.offsetY,
                0f,
            )
            assertTrue(saved.isEmpty())
        }
    }
}
