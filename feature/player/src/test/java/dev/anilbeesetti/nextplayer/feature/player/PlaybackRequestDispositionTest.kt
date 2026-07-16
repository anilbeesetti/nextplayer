package dev.anilbeesetti.nextplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRequestDispositionTest {

    @Test
    fun newIntentForActiveRequestIsAlreadyActive() {
        val disposition = playbackRequestDisposition(
            isIntentNew = true,
            hasCurrentMediaItem = true,
            isRequestAlreadyActive = true,
        )

        assertEquals(PlaybackRequestDisposition.ALREADY_ACTIVE, disposition)
    }

    @Test
    fun existingIntentWithCurrentItemIsReturningFromBackground() {
        val disposition = playbackRequestDisposition(
            isIntentNew = false,
            hasCurrentMediaItem = true,
            isRequestAlreadyActive = true,
        )

        assertEquals(PlaybackRequestDisposition.RETURN_FROM_BACKGROUND, disposition)
    }

    @Test
    fun newIntentForDifferentRequestLoadsIt() {
        val disposition = playbackRequestDisposition(
            isIntentNew = true,
            hasCurrentMediaItem = true,
            isRequestAlreadyActive = false,
        )

        assertEquals(PlaybackRequestDisposition.LOAD, disposition)
    }
}
