package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.core.model.DoubleTapGesture
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.state.BrightnessState
import dev.anilbeesetti.nextplayer.feature.player.state.ControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.PictureInPictureState
import dev.anilbeesetti.nextplayer.feature.player.state.SeekGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.SubtitlePositionState
import dev.anilbeesetti.nextplayer.feature.player.state.TapGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomAndContentScaleState
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomEvent
import dev.anilbeesetti.nextplayer.feature.player.state.VolumeAndBrightnessGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.VolumeState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVideoZoomAndContentScaleState
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerZoomGesturesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pinchStillUpdatesVisibleStateAfterChangingContentScale() {
        val exoPlayer = composeRule.runOnIdle { ExoPlayer.Builder(composeRule.activity).build() }
        val player = object : ForwardingPlayer(exoPlayer) {
            override fun getDuration(): Long = 60_000
        }
        android.provider.Settings.System.putFloat(
            composeRule.activity.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS,
            128f,
        )
        val brightnessActivity = Robolectric.buildActivity(PlayerActivity::class.java).get()
        var contentScale by mutableStateOf(VideoContentScale.BEST_FIT)
        lateinit var zoomState: VideoZoomAndContentScaleState
        try {
            composeRule.setContent {
                val scope = rememberCoroutineScope()
                val zoom = rememberVideoZoomAndContentScaleState(
                    player = player,
                    initialContentScale = contentScale,
                    enableZoomGesture = true,
                    enablePanGesture = true,
                    onEvent = {
                        if (it is VideoZoomEvent.ContentScaleChanged) {
                            contentScale =
                                it.contentScale
                        }
                    },
                )
                SideEffect { zoomState = zoom }
                PlayerGestures(
                    modifier = Modifier.testTag("gestures"),
                    controlsVisibilityState = ControlsVisibilityState(
                        player,
                        5.seconds,
                        scope,
                    ),
                    tapGestureState = TapGestureState(
                        player,
                        10_000,
                        coroutineScope = scope,
                        doubleTapGesture = DoubleTapGesture.NONE,
                    ),
                    pictureInPictureState = PictureInPictureState(
                        player,
                        composeRule.activity,
                    ),
                    seekGestureState = SeekGestureState(player),
                    videoZoomAndContentScaleState = zoom,
                    volumeAndBrightnessGestureState = VolumeAndBrightnessGestureState(
                        VolumeState(
                            player,
                            composeRule.activity,
                            false,
                            scope,
                        ),
                        BrightnessState(brightnessActivity),
                        volumeGestureSensitivity = 1f,
                        brightnessGestureSensitivity = 1f,
                        coroutineScope = scope,
                    ),
                    subtitlePositionState = SubtitlePositionState(0f) {},
                    hasSubtitles = false,
                    canRepositionSubtitles = false,
                )
            }
            pinchOut()
            composeRule.runOnIdle {
                assertTrue(
                    "Initial pinch changes zoom",
                    zoomState.zoom > 1f,
                )
                zoomState.switchToNextVideoContentScale()
            }
            composeRule.waitForIdle()
            pinchOut()
            composeRule.runOnIdle {
                assertTrue(
                    "Pinch after mode change updates visible zoom",
                    zoomState.zoom > 1f,
                )
            }
        } finally {
            composeRule.runOnIdle { exoPlayer.release() }
        }
    }

    private fun pinchOut() {
        composeRule.onNodeWithTag("gestures").performTouchInput {
            pinch(
                Offset(
                    centerX - 40f,
                    centerY,
                ),
                Offset(
                    centerX + 40f,
                    centerY,
                ),
                Offset(
                    centerX - 100f,
                    centerY,
                ),
                Offset(
                    centerX + 100f,
                    centerY,
                ),
            )
        }
    }
}
