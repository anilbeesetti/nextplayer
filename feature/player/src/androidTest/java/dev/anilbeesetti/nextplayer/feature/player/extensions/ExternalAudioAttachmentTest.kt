package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.feature.player.service.audioTrackUriOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalAudioAttachmentTest {

    @Test
    fun audioTrackUriOrNullRejectsMissingArgument() {
        assertNull(Bundle().audioTrackUriOrNull())
    }

    @Test
    fun audioTrackUriOrNullRejectsBlankArgument() {
        assertNull(Bundle().apply { putString("audio_track_uri", "  ") }.audioTrackUriOrNull())
    }

    @Test
    fun addAdditionalAudioTrackPreservesCurrentPlaybackAndSiblingItems() {
        runOnMainThread {
            val firstSibling = mediaItem("content://video/first")
            val firstAudioUri = Uri.parse("content://audio/one")
            val currentItem = mediaItem("content://video/current", listOf(firstAudioUri))
            val lastSibling = mediaItem("content://video/last")
            val secondAudioUri = Uri.parse("content://audio/two")
            val player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()

            try {
                player.setMediaItems(listOf(firstSibling, currentItem, lastSibling))
                player.seekTo(1, 12_345L)
                player.playWhenReady = true

                val positionBefore = player.currentPosition
                val playWhenReadyBefore = player.playWhenReady

                player.addAdditionalAudioTrack(secondAudioUri)

                assertEquals(1, player.currentMediaItemIndex)
                assertEquals(positionBefore, player.currentPosition)
                assertEquals(playWhenReadyBefore, player.playWhenReady)
                assertEquals(
                    listOf(firstAudioUri, secondAudioUri),
                    player.currentMediaItem!!.mediaMetadata.externalAudioTrackUris,
                )
                assertEquals(firstSibling, player.getMediaItemAt(0))
                assertEquals(lastSibling, player.getMediaItemAt(2))
            } finally {
                player.release()
            }
        }
    }

    @Test
    fun addAdditionalAudioTrackDoesNothingWhenUriAlreadyExists() {
        runOnMainThread {
            val audioUri = Uri.parse("content://audio/existing")
            val currentItem = mediaItem("content://video/current", listOf(audioUri))
            val player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()

            try {
                player.setMediaItem(currentItem)
                player.seekTo(4_321L)
                player.playWhenReady = true

                player.addAdditionalAudioTrack(audioUri)

                assertSame(currentItem, player.currentMediaItem)
                assertEquals(4_321L, player.currentPosition)
                assertEquals(true, player.playWhenReady)
            } finally {
                player.release()
            }
        }
    }

    private fun mediaItem(mediaId: String, externalAudioTrackUris: List<Uri> = emptyList()): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(externalAudioTrackUris = externalAudioTrackUris)
                    .build(),
            )
            .build()
    }

    private fun runOnMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
