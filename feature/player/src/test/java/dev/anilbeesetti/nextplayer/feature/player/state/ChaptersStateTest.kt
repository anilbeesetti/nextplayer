package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Label
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.Chapter
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ChaptersStateTest {
    @Test
    fun readsVisibleChaptersAcrossTracksAndKeepsPlayableBoundaries() {
        val chapters = listOf(-1L, 30_000L, 10_000L, 10_000L, 60_000L, Long.MAX_VALUE)
            .mapIndexed { index, start ->
                Chapter.Builder().setStartTimeMs(start).setTitle(Label(null, "$index")).build()
            }
        val tracks = tracks(
            Metadata(chapters + Chapter.Builder().setStartTimeMs(20_000L).setHidden(true).build()),
            Metadata(chapters),
        )

        val playable = tracks.chapters(60_000L)
        assertEquals(listOf(10_000L, 30_000L), playable.map { it.startTimeMs })
        assertEquals("2", playable.first().title?.value)
        assertEquals(listOf(10_001L, 30_001L), tracks.chapters(60_000L, 1).map { it.startTimeMs })
        assertEquals(-1, playable.currentChapterIndex(0L))
        assertEquals(0, playable.currentChapterIndex(10_000L))
        assertEquals(0, playable.currentChapterIndex(29_999L))
        assertEquals(1, playable.currentChapterIndex(30_000L))
        assertEquals(1, playable.currentChapterIndex(60_000L))
        assertEquals(0, playable.currentChapterIndex(15_000L))
        assertTrue(tracks.chapters(0L).isEmpty())
        assertTrue(tracks.chapters(C.TIME_UNSET).isEmpty())
        assertTrue(Tracks.EMPTY.chapters(60_000L).isEmpty())
        assertEquals(-1, emptyList<Chapter>().currentChapterIndex(0L))
    }

    @Test
    fun convertsPeriodTimestampsToThePlaybackWindow() {
        val chapters = listOf(
            Chapter.Builder().setStartTimeMs(0).setEndTimeMs(5_000).build(),
            Chapter.Builder().setStartTimeMs(5_000).setEndTimeMs(15_000).setTitle(Label(null, "Opening")).build(),
            Chapter.Builder().setStartTimeMs(15_000).build(),
            Chapter.Builder().build(),
        )
        val playable = tracks(Metadata(chapters)).chapters(durationMs = 10_000, periodOffsetMs = -10_000)
        assertEquals(listOf(0L, 5_000L), playable.map { it.startTimeMs })
        assertEquals(listOf(5_000L, C.TIME_UNSET), playable.map { it.endTimeMs })
        assertEquals("Opening", playable.first().title?.value)
        assertEquals(listOf(20_000L, 25_000L, 35_000L), tracks(Metadata(chapters)).chapters(40_000, 20_000).map { it.startTimeMs })
    }

    @Test
    fun readsId3ChaptersAndIgnoresUnrelatedMetadata() {
        val title = TextInformationFrame("TIT2", null, listOf("Introduction"))
        val chapter = ChapterFrame("intro", 0, 10_000, C.INDEX_UNSET.toLong(), C.INDEX_UNSET.toLong(), arrayOf(title))
        val playable = tracks(Metadata(title, chapter), null).chapters(60_000)
        assertEquals(listOf(0L), playable.map { it.startTimeMs })
        assertEquals("Introduction", playable.single().title?.value)
        assertEquals(10_000L, playable.single().endTimeMs)
    }

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
}
