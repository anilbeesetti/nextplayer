package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaPresentationStateTest {
    @Test
    fun hidesSeekBufferingAndShowsBufferingAfterPlaybackResumes() = runTest {
        val player = Mockito.mock(Player::class.java)
        val listenerCaptor = ArgumentCaptor.forClass(Player.Listener::class.java)
        Mockito.`when`(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        val state = MediaPresentationState(player)
        val observation = backgroundScope.launch { state.observe() }
        runCurrent()
        Mockito.verify(player).addListener(listenerCaptor.capture())
        val listener = listenerCaptor.value

        fun dispatch(playbackState: Int, vararg events: Int) {
            Mockito.`when`(player.playbackState).thenReturn(playbackState)
            listener.onEvents(
                player,
                Player.Events(FlagSet.Builder().addAll(*events).build()),
            )
        }

        assertTrue(state.showBufferingIndicator)
        dispatch(
            Player.STATE_READY,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
        )
        val position = Mockito.mock(Player.PositionInfo::class.java)
        listener.onPositionDiscontinuity(
            position,
            position,
            Player.DISCONTINUITY_REASON_SEEK,
        )
        dispatch(
            Player.STATE_BUFFERING,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
        )
        assertFalse(state.showBufferingIndicator)

        dispatch(
            Player.STATE_READY,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
        )
        dispatch(
            Player.STATE_BUFFERING,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
        )
        assertTrue(state.showBufferingIndicator)

        listener.onPositionDiscontinuity(
            position,
            position,
            Player.DISCONTINUITY_REASON_SEEK,
        )
        dispatch(
            Player.STATE_BUFFERING,
            Player.EVENT_POSITION_DISCONTINUITY,
        )
        assertFalse(state.showBufferingIndicator)
        dispatch(
            Player.STATE_BUFFERING,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
        )
        assertTrue(state.showBufferingIndicator)

        observation.cancelAndJoin()
        Mockito.verify(player).removeListener(listener)
    }
}
