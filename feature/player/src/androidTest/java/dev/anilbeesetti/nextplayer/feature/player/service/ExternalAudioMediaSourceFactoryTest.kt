package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.FakeMediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.feature.player.extensions.externalAudioTrackUris
import dev.anilbeesetti.nextplayer.feature.player.extensions.setExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class ExternalAudioMediaSourceFactoryTest {

    @Test
    fun externalAudioUrisRoundTripThroughMediaMetadata() {
        val uris = listOf(
            Uri.parse("content://audio/first"),
            Uri.parse("file:///audio/second.m4a"),
        )

        val metadata = MediaMetadata.Builder()
            .setExtras(externalAudioTrackUris = uris)
            .build()

        assertEquals(uris, metadata.externalAudioTrackUris)
    }

    @Test
    fun mediaItemWithoutExternalAudioDelegatesUnchanged() {
        val recordingFactory = RecordingMediaSourceFactory()
        val factory = ExternalAudioMediaSourceFactory(recordingFactory)
        val mediaItem = MediaItem.fromUri("content://video/main")

        val result = factory.createMediaSource(mediaItem)

        assertEquals(listOf(mediaItem), recordingFactory.createdItems)
        assertSame(recordingFactory.createdSources.single(), result)
    }

    @Test
    fun mediaItemWithExternalAudioMergesPrimaryThenAudioSources() {
        val videoUri = Uri.parse("content://video/main")
        val firstAudioUri = Uri.parse("content://audio/first")
        val secondAudioUri = Uri.parse("file:///audio/second.m4a")
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(
                        externalAudioTrackUris = listOf(firstAudioUri, secondAudioUri),
                    )
                    .build(),
            )
            .build()
        val recordingFactory = RecordingMediaSourceFactory()
        val factory = ExternalAudioMediaSourceFactory(recordingFactory)

        val result = factory.createMediaSource(mediaItem)

        assertEquals(
            listOf(videoUri, firstAudioUri, secondAudioUri),
            recordingFactory.createdItems.map { it.localConfiguration!!.uri },
        )
        assertTrue(result is MergingMediaSource)
    }

    @Test
    fun externalAudioIsClippedToPrimaryMediaDuration() {
        val audioUri = Uri.parse("content://audio/longer-than-video")
        val mediaItem = MediaItem.Builder()
            .setUri("content://video/thirty-seconds")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setDurationMs(30_000)
                    .setExtras(externalAudioTrackUris = listOf(audioUri))
                    .build(),
            )
            .build()
        val factory = ExternalAudioMediaSourceFactory(RecordingMediaSourceFactory())

        val result = factory.createMediaSource(mediaItem)

        assertTrue(result is ClippingMediaSource)
    }

    private class RecordingMediaSourceFactory : MediaSource.Factory {
        val createdItems = mutableListOf<MediaItem>()
        val createdSources = mutableListOf<MediaSource>()

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            createdItems += mediaItem
            return FakeMediaSource().also(createdSources::add)
        }

        override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)

        override fun setDrmSessionManagerProvider(
            drmSessionManagerProvider: DrmSessionManagerProvider,
        ): MediaSource.Factory = this

        override fun setLoadErrorHandlingPolicy(
            loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
        ): MediaSource.Factory = this
    }
}
