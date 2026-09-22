package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Label
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.Chapter
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.state.rememberChaptersState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaybackParametersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ControlsBottomViewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun repeatedKeyboardSeeksAccumulateBelowOneSecondWhilePaused() {
        val player = composeRule.runOnIdle { TestPlayer() }
        val seekbarFocusRequester = FocusRequester()
        try {
            showControls(player, seekbarFocusRequester)
            composeRule.runOnIdle { seekbarFocusRequester.requestFocus() }
            val seekbar = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            seekbar.assertIsFocused()

            repeat(3) { index ->
                seekbar.performKeyInput { pressKey(Key.DirectionRight) }
                composeRule.runOnIdle {
                    assertEquals((index + 1) * 600L, player.currentPosition)
                    assertFalse(player.playWhenReady)
                }
            }
        } finally {
            composeRule.runOnIdle { player.release() }
        }
    }

    @Test
    fun pausedSeeksSelectChaptersAtExactSubsecondBoundaries() {
        val player = composeRule.runOnIdle { TestPlayer() }
        try {
            showControls(player, FocusRequester())
            composeRule.runOnIdle { player.seekTo(1_500) }
            composeRule.onNodeWithText("Second").assertExists()

            composeRule.runOnIdle { player.seekTo(1_750) }
            composeRule.onNodeWithText("Third").assertExists()

            composeRule.runOnIdle { player.seekTo(1_749) }
            composeRule.onNodeWithText("Second").assertExists()
            composeRule.runOnIdle { assertFalse(player.playWhenReady) }
        } finally {
            composeRule.runOnIdle { player.release() }
        }
    }

    @Test
    fun timeLabelChangesOnWholeSecondsAndSwitchesToRemainingTime() {
        val player = composeRule.runOnIdle { TestPlayer() }
        try {
            showControls(player, FocusRequester())
            composeRule.runOnIdle { player.seekTo(1_999) }
            composeRule.onNodeWithText("00:01 / 01:00").performTouchInput { click() }
            composeRule.onNodeWithText("-00:59 / 01:00").assertExists()

            composeRule.runOnIdle { player.seekTo(2_000) }
            composeRule.onNodeWithText("-00:58 / 01:00").performTouchInput { click() }
            composeRule.onNodeWithText("00:02 / 01:00").assertExists()
        } finally {
            composeRule.runOnIdle { player.release() }
        }
    }

    private fun showControls(player: Player, seekbarFocusRequester: FocusRequester) {
        composeRule.setContent {
            NextPlayerTheme {
                val progress = rememberProgressStateWithTickInterval(player)
                ControlsBottomView(
                    progressState = progress,
                    chaptersState = rememberChaptersState(player, progress),
                    repeatButtonState = rememberRepeatButtonState(player),
                    shuffleButtonState = rememberShuffleButtonState(player),
                    playbackParametersState = rememberPlaybackParametersState(player),
                    controlsAlignment = Alignment.Start,
                    videoContentScale = VideoContentScale.BEST_FIT,
                    isPipSupported = false,
                    seekBarModifier = Modifier.focusRequester(seekbarFocusRequester),
                    onChaptersClick = {},
                    onVideoContentScaleClick = {},
                    onVideoContentScaleLongClick = {},
                    onLockControlsClick = {},
                    onPictureInPictureClick = {},
                    onPlaybackSpeedClick = {},
                    onRotateClick = {},
                    onPlayInBackgroundClick = {},
                    onSeek = player::seekTo,
                    onSeekEnd = {},
                )
            }
        }
    }

    private class TestPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private val chapters = listOf(0L to "Opening", 1_500L to "Second", 1_750L to "Third").map { (start, title) ->
            Chapter.Builder().setStartTimeMs(start).setTitle(Label(null, title)).build()
        }
        private val tracks = Tracks(
            listOf(
                Tracks.Group(
                    TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setMetadata(Metadata(chapters)).build()),
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(true),
                ),
            ),
        )
        private var state = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_GET_TRACKS,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    )
                    .build(),
            )
            .setPlaylist(listOf(MediaItemData.Builder("chapters").setDurationUs(60_000_000).setTracks(tracks).build()))
            .setContentPositionMs(0)
            .setPlaybackState(Player.STATE_READY)
            .build()

        override fun getState(): State = state

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            state = state.buildUpon()
                .setContentPositionMs(positionMs)
                .setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK, positionMs)
                .build()
            return Futures.immediateVoidFuture()
        }
    }
}
