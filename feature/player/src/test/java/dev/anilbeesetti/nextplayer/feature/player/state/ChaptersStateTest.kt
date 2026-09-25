package dev.anilbeesetti.nextplayer.feature.player.state

import android.os.Looper
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
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.ui.compose.state.ProgressStateWithTickInterval
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
class ChaptersStateTest {
    @Test
    fun readsVisibleChaptersAcrossTracksAndKeepsPlayableBoundaries() = runTest {
        val chapters = listOf(-1L, 30_000L, 10_000L, 10_000L, 60_000L, Long.MAX_VALUE)
            .mapIndexed { index, start ->
                Chapter.Builder().setStartTimeMs(start).setTitle(Label(null, "$index")).build()
            }
        val tracks = tracks(
            Metadata(chapters + Chapter.Builder().setStartTimeMs(20_000L).setHidden(true).build()),
            Metadata(chapters),
        )

        val playable = chapters(tracks, 60_000L)
        assertEquals(listOf(10_000L, 30_000L), playable.map { it.startTimeMs })
        assertEquals("2", playable.first().title?.value)
        assertEquals(listOf(10_001L, 30_001L), chapters(tracks, 60_000L, 1).map { it.startTimeMs })
        assertEquals(-1, playable.currentChapterIndex(0L))
        assertEquals(0, playable.currentChapterIndex(10_000L))
        assertEquals(0, playable.currentChapterIndex(29_999L))
        assertEquals(1, playable.currentChapterIndex(30_000L))
        assertEquals(1, playable.currentChapterIndex(60_000L))
        assertEquals(0, playable.currentChapterIndex(15_000L))
        assertTrue(chapters(tracks, 0L).isEmpty())
        assertTrue(chapters(tracks, C.TIME_UNSET).isEmpty())
        assertTrue(chapters(Tracks.EMPTY, 60_000L).isEmpty())
        assertEquals(-1, emptyList<Chapter>().currentChapterIndex(0L))
    }

    @Test
    fun convertsPeriodTimestampsToThePlaybackWindow() = runTest {
        val chapters = listOf(
            Chapter.Builder().setStartTimeMs(0).setEndTimeMs(5_000).build(),
            Chapter.Builder().setStartTimeMs(5_000).setEndTimeMs(15_000).setTitle(Label(null, "Opening")).build(),
            Chapter.Builder().setStartTimeMs(15_000).build(),
            Chapter.Builder().build(),
        )
        val tracks = tracks(Metadata(chapters))
        val playable = chapters(tracks, durationMs = 10_000, periodOffsetMs = -10_000)
        assertEquals(listOf(0L, 5_000L), playable.map { it.startTimeMs })
        assertEquals(listOf(5_000L, C.TIME_UNSET), playable.map { it.endTimeMs })
        assertEquals("Opening", playable.first().title?.value)
        assertEquals(listOf(20_000L, 25_000L, 35_000L), chapters(tracks, 40_000, 20_000).map { it.startTimeMs })
    }

    @Test
    fun readsId3ChaptersAndIgnoresUnrelatedMetadata() = runTest {
        val title = TextInformationFrame("TIT2", null, listOf("Introduction"))
        val chapter = ChapterFrame("intro", 0, 10_000, C.INDEX_UNSET.toLong(), C.INDEX_UNSET.toLong(), arrayOf(title))
        val playable = chapters(tracks(Metadata(title, chapter), null), 60_000)
        assertEquals(listOf(0L), playable.map { it.startTimeMs })
        assertEquals("Introduction", playable.single().title?.value)
        assertEquals(10_000L, playable.single().endTimeMs)
    }

    @Test
    fun chaptersFollowMetadataPlaybackSeekingAndMediaChanges() = runTest {
        val chapters = listOf(0L, 20_000L, 45_000L).map { Chapter.Builder().setStartTimeMs(it).build() }
        val item = SimpleBasePlayer.MediaItemData.Builder("chapters")
            .setDurationUs(60_000_000)
            .setTracks(tracks(Metadata(chapters)))
            .build()
        var positionMs = 25_000L
        val player = TestPlayer(
            playerState(item).buildUpon()
                .setContentPositionMs { positionMs }
                .build(),
        )
        val progress = ProgressStateWithTickInterval(player, 1_000, backgroundScope)
        val state = ChaptersState(player, progress)
        val progressObserver = backgroundScope.launch { progress.observe() }
        val chaptersObserver = backgroundScope.launch { state.observe() }
        try {
            runCurrent()
            assertEquals(chapters, state.chapters)
            assertEquals(1, state.currentChapterIndex)

            player.update(player.currentState.buildUpon().setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build())
            runCurrent()
            positionMs = 45_000L
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, state.currentChapterIndex)

            positionMs = 0L
            player.update(
                player.currentState.buildUpon()
                    .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    .setPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK, positionMs)
                    .build(),
            )
            assertEquals(0, state.currentChapterIndex)

            player.update(
                player.currentState.buildUpon()
                    .clearPositionDiscontinuity()
                    .setPlaylist(listOf(item.buildUpon().setTracks(Tracks.EMPTY).build()))
                    .build(),
            )
            assertTrue(state.chapters.isEmpty())
            assertEquals(-1, state.currentChapterIndex)

            player.update(player.currentState.buildUpon().setPlaylist(listOf(item)).build())
            assertEquals(chapters, state.chapters)
            assertEquals(0, state.currentChapterIndex)

            player.update(player.currentState.buildUpon().setPlaylist(emptyList()).setPlaybackState(Player.STATE_IDLE).build())
            assertTrue(state.chapters.isEmpty())
            assertEquals(-1, state.currentChapterIndex)

            chaptersObserver.cancelAndJoin()
            player.update(player.currentState.buildUpon().setPlaylist(listOf(item)).build())
            assertTrue(state.chapters.isEmpty())
        } finally {
            chaptersObserver.cancelAndJoin()
            progressObserver.cancelAndJoin()
            player.release()
        }
    }

    private fun TestScope.chapters(tracks: Tracks, durationMs: Long, periodOffsetMs: Long = 0): List<Chapter> {
        val item = SimpleBasePlayer.MediaItemData.Builder("chapters")
            .setDurationUs(if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1_000)
            .setTracks(tracks)
            .apply {
                if (periodOffsetMs > 0) {
                    setPeriods(
                        listOf(
                            SimpleBasePlayer.PeriodData.Builder("before").setDurationUs(periodOffsetMs * 1_000).build(),
                            SimpleBasePlayer.PeriodData.Builder("current").setDurationUs((durationMs - periodOffsetMs) * 1_000).build(),
                        ),
                    )
                } else {
                    setPositionInFirstPeriodUs(-periodOffsetMs * 1_000)
                }
            }
            .build()
        val player = TestPlayer(playerState(item).buildUpon().setContentPositionMs(periodOffsetMs.coerceAtLeast(0)).build())
        try {
            val progress = ProgressStateWithTickInterval(player, 1_000, backgroundScope)
            return ChaptersState(player, progress).chapters
        } finally {
            player.release()
        }
    }

    private fun playerState(item: SimpleBasePlayer.MediaItemData): SimpleBasePlayer.State = SimpleBasePlayer.State.Builder()
        .setAvailableCommands(
            Player.Commands.Builder()
                .addAll(Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_TRACKS, Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .build(),
        )
        .setPlaylist(listOf(item))
        .setPlaybackState(Player.STATE_READY)
        .build()

    private fun tracks(vararg metadata: Metadata?): Tracks = Tracks(
        metadata.mapIndexed { index, entries ->
            Tracks.Group(
                TrackGroup("$index", Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setMetadata(entries).build()),
                false,
                intArrayOf(C.FORMAT_HANDLED),
                booleanArrayOf(true),
            )
        },
    )

    private class TestPlayer(var currentState: State) : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State = currentState

        fun update(state: State) {
            currentState = state
            invalidateState()
            ShadowLooper.idleMainLooper()
        }
    }
}
