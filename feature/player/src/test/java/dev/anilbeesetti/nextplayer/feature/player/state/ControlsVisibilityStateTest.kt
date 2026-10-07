package dev.anilbeesetti.nextplayer.feature.player.state

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ControlsVisibilityStateTest {
    @Test
    fun zeroTimeoutKeepsControlsVisibleAndAllowsManualHiding() = runTest {
        val controls = ControlsVisibilityState(TestPlayer(), Duration.ZERO, backgroundScope)

        controls.showControls()
        advanceTimeBy(11.minutes.inWholeMilliseconds)
        runCurrent()
        assertTrue(controls.controlsVisible)

        controls.toggleControlsVisibility()
        assertFalse(controls.controlsVisible)
        controls.toggleControlsVisibility()
        controls.lockControls()
        controls.unlockControls()
        advanceTimeBy(11.minutes.inWholeMilliseconds)
        runCurrent()
        assertTrue(controls.controlsVisible)
        assertFalse(controls.controlsLocked)
    }

    @Test
    fun zeroTimeoutKeepsControlsVisibleWhenPlaybackStarts() = runTest {
        val player = TestPlayer(playing = false)
        val controls = ControlsVisibilityState(player, Duration.ZERO, backgroundScope)
        backgroundScope.launch { controls.observe() }
        runCurrent()

        player.startPlayback()
        advanceTimeBy(11.minutes.inWholeMilliseconds)
        runCurrent()
        assertTrue(player.isPlaying)
        assertTrue(controls.controlsVisible)
    }

    @Test
    fun tenMinuteTimeoutHidesControlsOnlyAfterTheFullDuration() = runTest {
        val controls = ControlsVisibilityState(TestPlayer(), 10.minutes, backgroundScope)

        controls.showControls()
        advanceTimeBy(10.minutes.inWholeMilliseconds - 1)
        runCurrent()
        assertTrue(controls.controlsVisible)

        advanceTimeBy(1)
        runCurrent()
        assertFalse(controls.controlsVisible)
    }

    @Test
    fun showingControlsWithZeroDurationCancelsAnExistingTimer() = runTest {
        val controls = ControlsVisibilityState(TestPlayer(), 4.seconds, backgroundScope)

        controls.showControls()
        runCurrent()
        advanceTimeBy(1.seconds.inWholeMilliseconds)
        controls.showControls(Duration.ZERO)
        advanceTimeBy(10.minutes.inWholeMilliseconds)
        runCurrent()
        assertTrue(controls.controlsVisible)
    }

    private class TestPlayer(playing: Boolean = true) : SimpleBasePlayer(Looper.getMainLooper()) {
        private var currentState = State.Builder()
            .setPlaylist(listOf(MediaItemData.Builder("video").setDurationUs(20.minutes.inWholeMicroseconds).build()))
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()

        override fun getState(): State = currentState

        fun startPlayback() {
            currentState = currentState.buildUpon()
                .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
            invalidateState()
            ShadowLooper.idleMainLooper()
        }
    }
}
