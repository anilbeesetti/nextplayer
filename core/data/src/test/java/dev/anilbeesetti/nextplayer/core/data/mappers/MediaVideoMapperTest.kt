package dev.anilbeesetti.nextplayer.core.data.mappers

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.media.services.MediaVideo
import dev.anilbeesetti.nextplayer.core.model.isNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class MediaVideoMapperTest {
    @Test
    fun `restored video uses original dates instead of fresh MediaStore dates`() {
        val now = System.currentTimeMillis()
        val mediaVideo = mediaVideoWithDates(now / 1000L)
        val state = MediumStateEntity(
            uriString = mediaVideo.uri.toString(),
            originalDateAdded = mediaVideo.dateAdded - 30 * 24 * 60 * 60L,
            originalDateModified = 123L,
        )

        val video = mediaVideo.toVideo(state)

        assertEquals(state.originalDateAdded, video.dateAdded)
        assertEquals(123L, video.dateModified)
        assertFalse(video.isNew(now))
        assertTrue(mediaVideo.toVideo().isNew(now))
        assertEquals(mediaVideo.dateModified, mediaVideo.toVideo().dateModified)
        assertEquals(mediaVideo.dateAdded, mediaVideo.toVideo(MediumStateEntity(uriString = state.uriString)).dateAdded)
    }

    @Test
    fun `legacy vault video with unknown dates does not become new on restore`() {
        val mediaVideo = mediaVideoWithDates(System.currentTimeMillis() / 1000L)
        val video = mediaVideo.toVideo(
            MediumStateEntity(
                uriString = mediaVideo.uri.toString(),
                originalDateAdded = 0,
                originalDateModified = 0,
            ),
        )

        assertEquals(0L, video.dateAdded)
        assertEquals(0L, video.dateModified)
        assertFalse(video.isNew())
    }

    private fun mediaVideoWithDates(date: Long) = MediaVideo(
        id = 1,
        uri = Uri.parse("content://media/external/video/media/1"),
        path = "/storage/emulated/0/Movies/video.mp4",
        title = "video.mp4",
        parentPath = "/storage/emulated/0/Movies",
        displayName = "video.mp4",
        duration = 1000,
        size = 100,
        width = 320,
        height = 180,
        dateModified = date,
        dateAdded = date,
    )

    @Test
    fun `trashed video uses MediaStore display name and preserves its path and uri`() {
        val mediaVideo = MediaVideo(
            id = 1,
            uri = Uri.parse("content://media/external/video/media/1"),
            path = "/storage/emulated/0/Movies/.trashed-1234567890-Holiday.video.mp4",
            title = ".trashed-1234567890-Holiday.video.mp4",
            parentPath = "/storage/emulated/0/Movies",
            displayName = "Holiday.video.mp4",
            duration = 1000,
            size = 100,
            width = 320,
            height = 180,
            dateModified = 1,
            dateAdded = 2,
        )

        val video = mediaVideo.toVideo()

        assertEquals("Holiday.video.mp4", video.nameWithExtension)
        assertEquals("Holiday.video", video.displayName)
        assertEquals(mediaVideo.path, video.path)
        assertEquals(mediaVideo.uri.toString(), video.uriString)
    }
}
