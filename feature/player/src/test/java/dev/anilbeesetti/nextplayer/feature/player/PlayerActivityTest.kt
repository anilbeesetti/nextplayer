package dev.anilbeesetti.nextplayer.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerActivityTest {

    @Test
    fun currentUriWithExplicitPlaylistStartsNewPlaybackQueue() {
        assertFalse(
            shouldResumeExistingPlayback(
                returningFromBackground = false,
                isRequestedUriCurrent = true,
                hasExplicitPlaylist = true,
            ),
        )
    }

    @Test
    fun currentUriWithoutExplicitPlaylistResumesExistingPlayback() {
        assertTrue(
            shouldResumeExistingPlayback(
                returningFromBackground = false,
                isRequestedUriCurrent = true,
                hasExplicitPlaylist = false,
            ),
        )
    }

    @Test
    fun returningFromBackgroundAlwaysResumesExistingPlayback() {
        assertTrue(
            shouldResumeExistingPlayback(
                returningFromBackground = true,
                isRequestedUriCurrent = true,
                hasExplicitPlaylist = true,
            ),
        )
    }
}
