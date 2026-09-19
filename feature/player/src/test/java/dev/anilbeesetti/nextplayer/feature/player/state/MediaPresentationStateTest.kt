package dev.anilbeesetti.nextplayer.feature.player.state

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.Chapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaPresentationStateTest {
    @Test
    fun chaptersFollowMetadataPlaybackSeekingAndMediaChanges() = runTest {
        val chapters = listOf(0L, 20_000L, 45_000L).map { Chapter.Builder().setStartTimeMs(it).build() }
        val tracks = Tracks(
            listOf(
                Tracks.Group(
                    TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setMetadata(Metadata(chapters)).build()),
                    false,
                    intArrayOf(C.FORMAT_HANDLED),
                    booleanArrayOf(true),
                ),
            ),
        )
        val item = SimpleBasePlayer.MediaItemData.Builder("chapters").setDurationUs(60_000_000).setTracks(tracks).build()
        var positionMs = 25_000L
        val player = TestPlayer(
            SimpleBasePlayer.State.Builder()
                .setAvailableCommands(
                    Player.Commands.Builder()
                        .addAll(Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_TRACKS, Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .build(),
                )
                .setPlaylist(listOf(item))
                .setPlaybackState(Player.STATE_READY)
                .setContentPositionMs { positionMs }
                .build(),
        )
        val presentation = MediaPresentationState(player)
        val observer = backgroundScope.launch { presentation.observe() }
        runCurrent()

        assertEquals(chapters, presentation.chapters)
        assertEquals(1, presentation.currentChapterIndex)

        player.update(player.currentState.buildUpon().setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build())
        positionMs = 45_000L
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, presentation.currentChapterIndex)

        positionMs = 0L
        player.update(
            player.currentState.buildUpon()
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK, positionMs)
                .build(),
        )
        assertEquals(0, presentation.currentChapterIndex)

        player.update(
            player.currentState.buildUpon()
                .clearPositionDiscontinuity()
                .setPlaylist(listOf(item.buildUpon().setTracks(Tracks.EMPTY).build()))
                .build(),
        )
        assertTrue(presentation.chapters.isEmpty())
        assertEquals(-1, presentation.currentChapterIndex)

        player.update(player.currentState.buildUpon().setPlaylist(listOf(item)).build())
        assertEquals(chapters, presentation.chapters)
        assertEquals(0, presentation.currentChapterIndex)

        player.update(player.currentState.buildUpon().setPlaylist(emptyList()).setPlaybackState(Player.STATE_IDLE).build())
        assertTrue(presentation.chapters.isEmpty())
        assertEquals(-1, presentation.currentChapterIndex)

        observer.cancelAndJoin()
        player.update(player.currentState.buildUpon().setPlaylist(listOf(item)).build())
        assertTrue(presentation.chapters.isEmpty())
    }

    private class TestPlayer(var currentState: State) : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State = currentState

        fun update(state: State) {
            currentState = state
            invalidateState()
            ShadowLooper.idleMainLooper()
        }
    }
}
