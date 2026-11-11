package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaylistStateTest {
    @Test
    fun removesRequestedItemAndPreservesRemainingOrder() = withPlaylist(
        "first",
        "middle",
        "last",
    ) {
            state,
            player,
        ->
        state.removeItem(index = 1)

        assertEquals(
            listOf(
                "first",
                "last",
            ),
            player.mediaIds(),
        )
    }

    @Test
    fun ignoresOutOfBoundsRemoval() = withPlaylist(
        "first",
        "last",
    ) { state, player ->
        for (index in listOf(
            -1,
            2,
            3,
        )) {
            state.removeItem(index = index)
            assertEquals(
                listOf(
                    "first",
                    "last",
                ),
                player.mediaIds(),
            )
        }
    }

    @Test
    fun removingCurrentFinalRowSelectsPreviousItemAndPreservesPlayingIntent() = withPlaylist(
        "first",
        "middle",
        "last",
    ) { state, player ->
        for (playing in listOf(
            false,
            true,
        )) {
            player.setMediaItems(
                listOf(
                    "first",
                    "middle",
                    "last",
                ).map {
                    MediaItem.Builder().setMediaId(it).setUri("file:///$it.mp4").build()
                },
            )
            player.seekToDefaultPosition(2)
            player.playWhenReady = playing
            state.removeItem(2)
            assertEquals(
                listOf(
                    "first",
                    "middle",
                ),
                player.mediaIds(),
            )
            assertEquals(
                "middle",
                player.currentMediaItem?.mediaId,
            )
            assertEquals(
                playing,
                player.playWhenReady,
            )
        }
    }

    @Test
    fun cannotRemoveCurrentFinalRowWithoutTheCommandToKeepPlaybackActive() = withPlaylist(
        "first",
        "last",
    ) { _, player ->
        player.seekToDefaultPosition(1)
        val limited = object : ForwardingPlayer(player) {
            override fun isCommandAvailable(command: Int): Boolean =
                command != Player.COMMAND_SEEK_TO_MEDIA_ITEM && super.isCommandAvailable(command)
        }
        val state = PlaylistState(limited)
        org.junit.Assert.assertFalse(state.canRemoveItem(1))
        org.junit.Assert.assertTrue(state.canRemoveItem(0))
        state.removeItem(1)
        assertEquals(
            listOf(
                "first",
                "last",
            ),
            player.mediaIds(),
        )
    }

    @Test
    fun preservesLastItem() = withPlaylist("only") { state, player ->
        state.removeItem(index = 0)

        assertEquals(
            listOf(element = "only"),
            player.mediaIds(),
        )
    }

    @Test
    fun duplicateUrisHaveDistinctStableKeysAndKnownDurations() = withPlaylist(
        "same",
        "same",
    ) {
            state,
            player,
        ->
        val firstKey = state.getKeyAt(0)
        org.junit.Assert.assertNotEquals(
            firstKey,
            state.getKeyAt(1),
        )
        val item = player.getMediaItemAt(1).buildUpon().setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder().setDurationMs(20_000).build(),
        ).build()
        player.replaceMediaItem(
            1,
            item,
        )
        assertEquals(
            firstKey,
            state.getKeyAt(0),
        )
        assertEquals(
            20_000L,
            PlaylistState(player).getDurationAt(1),
        )
        assertEquals(
            null,
            state.getDurationAt(0),
        )
    }

    private fun withPlaylist(vararg ids: String, assertion: (PlaylistState, Player) -> Unit) {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            player.setMediaItems(
                ids.map {
                    MediaItem.Builder().setMediaId(it).setUri("file:///$it.mp4").build()
                },
            )
            assertion(
                PlaylistState(player = player),
                player,
            )
        } finally {
            player.release()
        }
    }

    private fun Player.mediaIds(): List<String> = (0 until mediaItemCount).map {
        getMediaItemAt(it).mediaId
    }
}
