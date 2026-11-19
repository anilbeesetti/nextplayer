package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerTest {
    @Test
    fun remoteSubtitleReloadKeepsQueuePositionAndPlayStateAndSelectsOnlyNewSubtitle() {
        val local = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        val remote = object : ForwardingPlayer(local) {
            override fun getDeviceInfo() =
                DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
        }
        try {
            val previous = MediaItem.SubtitleConfiguration.Builder(
                android.net.Uri.parse("file:///old.vtt"),
            )
                .setId("old").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            val items = listOf(
                "before",
                "current",
                "after",
            ).map {
                MediaItem.Builder().setMediaId(it).setUri("file:///$it.mp4")
                    .setSubtitleConfigurations(
                        listOf(previous),
                    ).build().copy(subtitleTrackIndex = 7)
            }
            remote.setMediaItems(
                items,
                1,
                12_000,
            )
            for (playing in listOf(
                false,
                true,
            )) {
                remote.playWhenReady = playing
                remote.addAdditionalSubtitleConfiguration(
                    MediaItem.SubtitleConfiguration.Builder(
                        android.net.Uri.parse("file:///new-$playing.srt"),
                    )
                        .setId("new-$playing").build(),
                )
                assertEquals(
                    items.map {
                        it.mediaId
                    },
                    (0 until remote.mediaItemCount).map { remote.getMediaItemAt(it).mediaId },
                )
                assertEquals(
                    1,
                    remote.currentMediaItemIndex,
                )
                assertEquals(
                    12_000L,
                    remote.currentPosition,
                )
                assertEquals(
                    playing,
                    remote.playWhenReady,
                )
                assertEquals(
                    7,
                    remote.currentMediaItem?.mediaMetadata?.subtitleTrackIndex,
                )
                val subtitles = checkNotNull(
                    remote.currentMediaItem?.localConfiguration,
                ).subtitleConfigurations
                assertEquals(
                    "new-$playing",
                    subtitles.single {
                        it.selectionFlags and
                            C.SELECTION_FLAG_DEFAULT !=
                            0
                    }.id,
                )
            }
        } finally {
            remote.release()
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
            val items = listOf(
                "before",
                "current",
                "after",
            ).map {
                MediaItem.Builder().setMediaId(
                    it,
                ).setUri("file:///$it.mp4").build().copy(positionMs = 2_000)
            }
            val subtitle = MediaItem.SubtitleConfiguration.Builder(
                android.net.Uri.parse("file:///test.srt"),
            )
                .setId("test").setMimeType(MimeTypes.APPLICATION_SUBRIP).build()
            player.setMediaItems(
                items,
                1,
                12_000,
            )
            player.replaceMediaItem(
                1,
                items[1].copy(
                    videoDecoderMode = DecoderMode.HARDWARE,
                    audioDecoderMode = DecoderMode.FFMPEG,
                ),
            )
            val shuffleOrder = ShuffleOrder.DefaultShuffleOrder(
                intArrayOf(
                    2,
                    1,
                    0,
                ),
                0,
            )
            player.setShuffleOrder(shuffleOrder)
            player.shuffleModeEnabled = true
            val transitions = mutableListOf<MediaItem?>()
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    transitions += mediaItem
                }
            })

            for (playing in listOf(
                false,
                true,
            )) {
                player.playWhenReady = playing
                createdItems.clear()
                player.addAdditionalSubtitleConfiguration(
                    subtitle.buildUpon().setId("test-$playing").build(),
                )

                assertEquals(
                    "Only the current source should be recreated",
                    listOf(player.currentMediaItem),
                    createdItems,
                )
                assertEquals(
                    shuffleOrder,
                    player.shuffleOrder,
                )
                assertTrue(player.shuffleModeEnabled)
                assertEquals(
                    items.map { it.mediaId },
                    (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                )
                assertEquals(
                    1,
                    player.currentMediaItemIndex,
                )
                assertEquals(
                    12_000L,
                    player.currentPosition,
                )
                assertEquals(
                    12_000L,
                    player.mediaMetadata.positionMs,
                )
                assertEquals(
                    playing,
                    player.playWhenReady,
                )
                assertTrue(transitions.isNotEmpty())
                assertTrue(
                    transitions.all {
                        it?.mediaId == "current" &&
                            it.mediaMetadata.videoDecoderMode == DecoderMode.HARDWARE &&
                            it.mediaMetadata.audioDecoderMode == DecoderMode.FFMPEG
                    },
                )
            }
            assertEquals(
                2,
                player.currentMediaItem?.localConfiguration?.subtitleConfigurations?.size,
            )
            assertEquals(
                0,
                player.mediaMetadata.subtitleTrackIndex,
            )

            val currentItem = player.currentMediaItem
            createdItems.clear()
            player.addAdditionalSubtitleConfiguration(
                subtitle.buildUpon().setId("test-true").build(),
            )
            assertEquals(
                currentItem,
                player.currentMediaItem,
            )
            assertTrue(createdItems.isEmpty())
        } finally {
            player.release()
        }
    }
}
