package dev.anilbeesetti.nextplayer.feature.player

import androidx.core.net.toUri
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackArtworkModelTest {
    private val thumbnail = "file:///data/thumbnails/cached".toUri()

    @Test fun localVideoKeepsItsOwnFrameWhateverTheArtworkMetadataBecomes() {
        val video = "file:///storage/Movies/a.mp4"
        listOf(
            MediaMetadata.EMPTY,
            MediaMetadata.Builder().setArtworkUri("android.resource://app/1".toUri()).build(),
            MediaMetadata.Builder().setArtworkUri(thumbnail).build(),
            MediaMetadata.Builder().setArtworkData(
                byteArrayOf(1),
                MediaMetadata.PICTURE_TYPE_FRONT_COVER,
            ).build(),
        ).forEach {
            assertEquals(
                video.toUri(),
                playbackArtworkModel(
                    snapshot(
                        video,
                        it,
                    ),
                ),
            )
        }
    }

    @Test fun networkVideoUsesItsSuppliedArtwork() {
        assertEquals(
            thumbnail,
            playbackArtworkModel(
                snapshot(
                    "https://example.com/a.mp4",
                    MediaMetadata.Builder().setArtworkUri(thumbnail).build(),
                ),
            ),
        )
    }

    private fun snapshot(uri: String, metadata: MediaMetadata) = PlaybackSnapshot(
        item = MediaItem.Builder().setUri(uri).build(),
        metadata = metadata,
        playbackState = Player.STATE_READY,
        playWhenReady = true,
        deviceInfo = DeviceInfo.UNKNOWN,
        duration = 0,
        position = 0,
        commands = Player.Commands.EMPTY,
        deviceVolume = 0,
        volume = 1f,
        shuffle = false,
        repeatMode = Player.REPEAT_MODE_OFF,
    )
}
