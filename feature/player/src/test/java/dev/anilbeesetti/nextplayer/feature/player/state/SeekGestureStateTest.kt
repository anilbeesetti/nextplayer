package dev.anilbeesetti.nextplayer.feature.player.state

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.feature.player.extensions.detectCustomHorizontalDragGestures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SeekGestureStateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun replacingPlayerOrRemovingStateEndsPreviousSeek() {
        var player: Player by mutableStateOf(TestPlayer())
        var visible by mutableStateOf(true)
        var state: SeekGestureState? = null
        composeRule.setContent {
            if (visible) state = rememberSeekGestureState(player, enableSeekGesture = true)
        }
        val first = composeRule.runOnIdle { requireNotNull(state).also { it.onSeek(5_000) } }
        composeRule.runOnIdle {
            assertTrue(first.isSeeking)
            player = TestPlayer()
        }
        composeRule.runOnIdle {
            assertFalse(first.isSeeking)
            assertNotSame(first, state)
            requireNotNull(state).onSeek(8_000)
            assertEquals(8_000, player.currentPosition)
            visible = false
        }
        composeRule.runOnIdle { assertFalse(requireNotNull(state).isSeeking) }
    }

    @Test
    fun cancellingHorizontalDetectorEndsSeekWithoutPointerUp() {
        val player = TestPlayer()
        val state = SeekGestureState(player)
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            Box(
                Modifier.size(300.dp).testTag("gesture").pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectCustomHorizontalDragGestures(
                        onDragStart = state::onDragStart,
                        onHorizontalDrag = state::onDrag,
                        onDragEnd = state::onDragEnd,
                        onDragCancel = state::onDragEnd,
                    )
                },
            )
        }
        composeRule.onNodeWithTag("gesture").performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.5f, centerY))
            moveTo(Offset(width * 0.7f, centerY))
        }
        composeRule.runOnIdle {
            assertTrue(state.isSeeking)
            enabled = false
        }
        composeRule.runOnIdle { assertFalse(state.isSeeking) }
        composeRule.onNodeWithTag("gesture").performTouchInput { cancel() }
    }

    private class TestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var state = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM).build())
            .setPlaylist(listOf(MediaItemData.Builder("video").setDurationUs(60_000_000).setIsSeekable(true).build()))
            .setContentPositionMs(1_000)
            .setPlaybackState(Player.STATE_READY)
            .build()

        override fun getState(): State = state

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            state = state.buildUpon().setContentPositionMs(positionMs).build()
            return Futures.immediateVoidFuture()
        }
    }
}
