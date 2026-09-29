package dev.anilbeesetti.nextplayer.feature.player.buttons

import android.content.pm.ActivityInfo
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.VideoSize
import dev.anilbeesetti.nextplayer.core.model.ScreenOrientation
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.state.PlayerOrientationEffect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "port")
class RotateButtonTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun videoOrientationContinuesFollowingVideoSizeWithoutRotateButton() {
        val player = composeRule.runOnIdle { TestPlayer() }
        try {
            composeRule.runOnIdle {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            composeRule.setContent {
                PlayerOrientationEffect(player, ScreenOrientation.VIDEO_ORIENTATION)
            }
            composeRule.runOnIdle {
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, composeRule.activity.requestedOrientation)
                player.updateVideoSize(VideoSize(360, 640))
            }
            composeRule.runOnIdle {
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, composeRule.activity.requestedOrientation)
                player.updateVideoSize(VideoSize(640, 360))
            }
            composeRule.runOnIdle {
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, composeRule.activity.requestedOrientation)
            }
        } finally {
            composeRule.runOnIdle { player.release() }
        }
    }

    @Test
    fun manualRotationSurvivesHidingAndRecreatingRotateButton() {
        val player = composeRule.runOnIdle { TestPlayer() }
        val visible = mutableStateOf(true)
        try {
            composeRule.runOnIdle {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            composeRule.setContent {
                PlayerOrientationEffect(player, ScreenOrientation.PORTRAIT)
                NextPlayerTheme {
                    if (visible.value) RotateButton()
                }
            }
            composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.screen_rotation)).performClick()
            composeRule.runOnIdle {
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, composeRule.activity.requestedOrientation)
                visible.value = false
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle { visible.value = true }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, composeRule.activity.requestedOrientation)
            }
        } finally {
            composeRule.runOnIdle { player.release() }
        }
    }

    private class TestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var state = State.Builder()
            .setAvailableCommands(Player.Commands.EMPTY)
            .setVideoSize(VideoSize(640, 360))
            .build()

        override fun getState(): State = state

        fun updateVideoSize(videoSize: VideoSize) {
            state = state.buildUpon().setVideoSize(videoSize).build()
            invalidateState()
        }
    }
}
