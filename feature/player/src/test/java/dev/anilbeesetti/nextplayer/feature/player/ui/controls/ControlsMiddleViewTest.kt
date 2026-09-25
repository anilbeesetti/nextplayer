package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
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
class ControlsMiddleViewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun enteringGroupFocusesPlayPauseIncludingReturnFromAnotherControl() {
        val groupFocusRequester = FocusRequester()
        val player = composeRule.runOnIdle { TestPlayer() }
        try {
            showControls(player, groupFocusRequester)
            val playPause = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.play_pause))
            val next = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.player_controls_next))

            composeRule.runOnIdle { groupFocusRequester.requestFocus() }
            playPause.assertIsFocused()

            playPause.performKeyInput { pressKey(Key.DirectionRight) }
            next.assertIsFocused()
            val otherControl = composeRule.onNodeWithTag("other-control")
            otherControl.requestFocus().assertIsFocused()

            otherControl.performKeyInput { pressKey(Key.DirectionUp) }
            playPause.assertIsFocused()
        } finally {
            composeRule.runOnIdle { player.release() }
        }
    }

    @Test
    fun activationKeysInvokeOnlyTheFocusedButton() {
        val groupFocusRequester = FocusRequester()
        val player = composeRule.runOnIdle { TestPlayer() }
        try {
            showControls(player, groupFocusRequester)
            val playPause = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.play_pause))
            val next = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.player_controls_next))
            val previous = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.player_controls_previous))
            composeRule.runOnIdle { groupFocusRequester.requestFocus() }

            for (key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                playPause.performKeyInput { pressKey(key) }
                composeRule.runOnIdle { assertTrue(player.playWhenReady) }
                playPause.performKeyInput { pressKey(key) }
                composeRule.runOnIdle { assertFalse(player.playWhenReady) }
            }

            playPause.performKeyInput { pressKey(Key.DirectionRight) }
            next.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
            composeRule.runOnIdle {
                assertEquals(2, player.currentMediaItemIndex)
                assertFalse(player.playWhenReady)
            }

            playPause.requestFocus().performKeyInput { pressKey(Key.DirectionLeft) }
            previous.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
            composeRule.runOnIdle {
                assertEquals(1, player.currentMediaItemIndex)
                assertFalse(player.playWhenReady)
            }
        } finally {
            composeRule.runOnIdle { player.release() }
        }
    }

    private fun showControls(player: Player, groupFocusRequester: FocusRequester) {
        composeRule.setContent {
            NextPlayerTheme {
                Column {
                    ControlsMiddleView(
                        modifier = Modifier.focusRequester(groupFocusRequester),
                        player = player,
                    )
                    Button(
                        modifier = Modifier.testTag("other-control"),
                        onClick = {},
                    ) {
                        Text("Other control")
                    }
                }
            }
        }
    }

    private class TestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var state = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                    )
                    .build(),
            )
            .setPlaylist(List(3) { MediaItemData.Builder(it).setDurationUs(60_000_000).build() })
            .setCurrentMediaItemIndex(1)
            .setContentPositionMs(0)
            .setPlaybackState(Player.STATE_READY)
            .build()

        override fun getState(): State = state

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            state = state.buildUpon().setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            state = state.buildUpon().setCurrentMediaItemIndex(mediaItemIndex).setContentPositionMs(0).build()
            return Futures.immediateVoidFuture()
        }
    }
}
