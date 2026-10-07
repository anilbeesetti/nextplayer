package dev.anilbeesetti.nextplayer.core.data.mappers

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.media.services.MediaVideo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class MediaVideoMapperTest {
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
