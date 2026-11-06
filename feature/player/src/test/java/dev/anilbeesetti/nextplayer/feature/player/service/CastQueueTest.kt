package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.MediaItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistItemRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistRecord
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.feature.player.utils.toMediaQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CastQueueTest {
    private val folder = (0..2).map {
        MediaItem.Builder().setMediaId("$it").setUri("file:///$it.mp4").build()
    }

    @Test
    fun folderAutoplayOffSendsOnlySelectedVideo() {
        assertEquals(
            listOf(folder[1]),
            mediaItemsForCast(
                folder,
                1,
                autoplay = false,
            ),
        )
    }

    @Test
    fun folderAutoplayOnSendsWholeQueue() {
        assertSame(
            folder,
            mediaItemsForCast(
                folder,
                1,
                autoplay = true,
            ),
        )
    }

    @Test
    fun explicitPlaylistPlaysInFullWithAutoplayOff() {
        val playlist = PlaylistRecord(
            id = 1,
            name = "Movies",
            type = PlaylistType.LOCAL,
            source = null,
            items = folder.mapIndexed { index, item ->
                PlaylistItemRecord(
                    position = index,
                    uri = item.mediaId,
                )
            },
            lastRefreshedAt = null,
        ).toMediaQueue(selectedUri = folder[1].mediaId)!!.mediaItems
        assertSame(
            playlist,
            mediaItemsForCast(
                playlist,
                1,
                autoplay = false,
            ),
        )
    }

    @Test
    fun unsetIndexSelectsFirstVideo() {
        assertEquals(
            listOf(folder.first()),
            mediaItemsForCast(
                folder,
                -1,
                autoplay = false,
            ),
        )
    }
}
