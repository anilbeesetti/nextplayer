package dev.anilbeesetti.nextplayer.feature.player.utils

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.extensions.isValidFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoRenameTest {
    private val videoUri = Uri.parse("content://media/external/video/media/42")

    @Test
    fun preservesExtensionWithoutDuplicatingIt() {
        val video = VideoRename(
            videoUri,
            "old.name.mp4",
        )

        assertEquals(
            "old.name",
            video.baseName,
        )
        assertEquals(
            "new.name.mp4",
            video.withBaseName("new.name"),
        )
        assertEquals(
            "new.name.mp4",
            video.withBaseName("new.name.mp4"),
        )
    }

    @Test
    fun acceptsOnlyMediaStoreVideoItems() {
        assertTrue(videoUri.isMediaStoreVideo())
        assertFalse(Uri.parse("content://media/external/video/media").isMediaStoreVideo())
        assertFalse(Uri.parse("content://media/external/audio/media/42").isMediaStoreVideo())
        assertFalse(Uri.parse("file:///storage/emulated/0/Movies/video.mp4").isMediaStoreVideo())
    }

    @Test
    fun rejectsNamesThatAreNotSingleFileNames() {
        assertFalse("../video".isValidFileName())
        assertFalse("folder\\video".isValidFileName())
        assertFalse("..".isValidFileName())
        assertTrue("my.video".isValidFileName())
    }
}
