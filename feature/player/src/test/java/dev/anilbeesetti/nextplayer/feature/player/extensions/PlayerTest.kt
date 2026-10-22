package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerTest {
    @Test
    fun backgroundPlaybackDisablesOnlyVideoAndRestoresItWithoutChangingTheSession() {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            val items = listOf("first", "second").map {
                MediaItem.Builder().setMediaId(it).setUri("file:///$it.mp4").build()
            }
            player.setMediaItems(items, 1, 12_000)
            player.setPlaybackSpeed(1.15f)
            val audioOverride = TrackSelectionOverride(
                TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
                0,
            )
            val selections = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(audioOverride)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            player.trackSelectionParameters = selections

            for (playing in listOf(true, false)) {
                player.playWhenReady = playing
                player.setVideoPlaybackEnabled(false)
                assertTrue(C.TRACK_TYPE_VIDEO in player.trackSelectionParameters.disabledTrackTypes)
                assertFalse(C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
                assertEquals(selections.overrides, player.trackSelectionParameters.overrides)
                assertTrue(C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes)

                player.setVideoPlaybackEnabled(true)
                assertEquals(selections, player.trackSelectionParameters)
                assertEquals(items, (0 until player.mediaItemCount).map(player::getMediaItemAt))
                assertEquals(1, player.currentMediaItemIndex)
                assertEquals(12_000L, player.currentPosition)
                assertEquals(playing, player.playWhenReady)
                assertEquals(1.15f, player.playbackParameters.speed)
            }
        } finally {
            player.release()
        }
    }

    @Test
    fun addingSubtitleReloadsOnlyCurrentSourceAndPreservesPlaybackState() {
        val context = RuntimeEnvironment.getApplication()
        val sourceFactory = DefaultMediaSourceFactory(context)
        val createdItems = mutableListOf<MediaItem>()
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(object : MediaSource.Factory by sourceFactory {
                override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                    createdItems += mediaItem
                    return sourceFactory.createMediaSource(mediaItem)
                }
            })
            .build()
        try {
            val items = listOf("before", "current", "after").map {
                MediaItem.Builder().setMediaId(it).setUri("file:///$it.mp4").build().copy(positionMs = 2_000)
            }
            val subtitle = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse("file:///test.srt"))
                .setId("test").setMimeType(MimeTypes.APPLICATION_SUBRIP).build()
            player.setMediaItems(items, 1, 12_000)
            player.replaceMediaItem(1, items[1].copy(videoDecoderMode = DecoderMode.HARDWARE, audioDecoderMode = DecoderMode.FFMPEG))
            val shuffleOrder = ShuffleOrder.DefaultShuffleOrder(intArrayOf(2, 1, 0), 0)
            player.setShuffleOrder(shuffleOrder)
            player.shuffleModeEnabled = true
            val transitions = mutableListOf<MediaItem?>()
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    transitions += mediaItem
                }
            })

            for (playing in listOf(false, true)) {
                player.playWhenReady = playing
                createdItems.clear()
                player.addAdditionalSubtitleConfiguration(subtitle.buildUpon().setId("test-$playing").build())

                assertEquals(
                    "Only the current source should be recreated",
                    listOf(player.currentMediaItem),
                    createdItems,
                )
                assertEquals(shuffleOrder, player.shuffleOrder)
                assertTrue(player.shuffleModeEnabled)
                assertEquals(items.map { it.mediaId }, (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
                assertEquals(1, player.currentMediaItemIndex)
                assertEquals(12_000L, player.currentPosition)
                assertEquals(12_000L, player.mediaMetadata.positionMs)
                assertEquals(playing, player.playWhenReady)
                assertTrue(transitions.isNotEmpty())
                assertTrue(transitions.all {
                    it?.mediaId == "current" &&
                        it.mediaMetadata.videoDecoderMode == DecoderMode.HARDWARE &&
                        it.mediaMetadata.audioDecoderMode == DecoderMode.FFMPEG
                })
            }
            assertEquals(2, player.currentMediaItem?.localConfiguration?.subtitleConfigurations?.size)
            assertEquals(0, player.mediaMetadata.subtitleTrackIndex)

            val currentItem = player.currentMediaItem
            createdItems.clear()
            player.addAdditionalSubtitleConfiguration(subtitle.buildUpon().setId("test-true").build())
            assertEquals(currentItem, player.currentMediaItem)
            assertTrue(createdItems.isEmpty())
        } finally {
            player.release()
        }
    }
}
