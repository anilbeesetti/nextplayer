package dev.anilbeesetti.nextplayer.feature.player.buttons

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackSpeedButtonTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun speedFollowsActivePlayerAcrossHiddenControlsAndPlayerReplacement() {
        val first = composeRule.runOnIdle { TestPlayer(1.25f) }
        val second = composeRule.runOnIdle { TestPlayer(2f) }
        val player = mutableStateOf<Player?>(first)
        val visible = mutableStateOf(true)
        try {
            composeRule.setContent {
                NextPlayerTheme {
                    if (visible.value) PlaybackSpeedButton(player = player.value, onClick = {})
                }
            }
            composeRule.onNodeWithText("1.25").assertExists()
            composeRule.runOnIdle { first.updateSpeed(1.5f) }
            composeRule.onNodeWithText("1.5").assertExists()
            composeRule.runOnIdle { visible.value = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                first.updateSpeed(1.75f)
                visible.value = true
            }
            composeRule.onNodeWithText("1.75").assertExists()
            composeRule.runOnIdle { player.value = second }
            composeRule.onNodeWithText("2.0").assertExists()
            composeRule.runOnIdle { first.updateSpeed(3f) }
            composeRule.onNodeWithText("2.0").assertExists()
            composeRule.runOnIdle { player.value = null }
            composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.select_playback_speed))
                .assertIsNotEnabled()
        } finally {
            composeRule.runOnIdle {
                first.release()
                second.release()
            }
        }
    }

    private class TestPlayer(speed: Float) : SimpleBasePlayer(Looper.getMainLooper()) {
        private var state = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().add(Player.COMMAND_SET_SPEED_AND_PITCH).build())
            .setPlaybackParameters(PlaybackParameters(speed))
            .build()

        override fun getState(): State = state

        fun updateSpeed(speed: Float) {
            state = state.buildUpon().setPlaybackParameters(PlaybackParameters(speed)).build()
            invalidateState()
        }
    }
}
