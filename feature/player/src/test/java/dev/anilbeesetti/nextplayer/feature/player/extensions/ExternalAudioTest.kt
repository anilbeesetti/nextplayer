package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
@UnstableApi
class ExternalAudioTest {
    @Test
    fun externalAudioIndexSurvivesSessionTrackGroupIds() {
        val track = TrackGroup("session-id-1:0", Format.Builder().setId("1:0").build())
        assertEquals(0, track.externalAudioIndex)
    }

    @Test
    fun audioSurvivesMetadataAndSubtitleChanges() {
        val audio = listOf(Uri.parse("content://documents/audio/one"), Uri.parse("file:///two.mp3"))
        val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse("file:///captions.srt")).build()
        val item = MediaItem.Builder()
            .setUri("file:///video.mp4")
            .setMediaId("video")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Video").build())
            .build()
            .withExternalAudio(audio)
            .copy(positionMs = 12000, audioTrackIndex = 2, playbackSpeed = 1.5f)
            .buildUpon().setSubtitleConfigurations(listOf(subtitle)).build()

        assertEquals(audio, item.mediaMetadata.externalAudio)
        assertEquals(12000L, item.mediaMetadata.positionMs)
        assertEquals(2, item.mediaMetadata.audioTrackIndex)
        assertEquals("Video", item.mediaMetadata.title)
        assertEquals(listOf(subtitle), item.localConfiguration!!.subtitleConfigurations)
        assertEquals(emptyList<Uri>(), item.withExternalAudio(emptyList()).mediaMetadata.externalAudio)
    }
}
