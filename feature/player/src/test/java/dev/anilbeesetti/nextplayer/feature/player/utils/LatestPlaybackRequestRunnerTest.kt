package dev.anilbeesetti.nextplayer.feature.player.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestPlaybackRequestRunnerTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun secondSubmissionCancelsTheFirst() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        val completed = mutableListOf<String>()
        val runner = LatestPlaybackRequestRunner(backgroundScope)

        runner.submit {
            firstStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                firstCancelled.complete(Unit)
            }
        }
        firstStarted.await()
        runner.submit { completed += "second" }
        advanceUntilIdle()

        firstCancelled.await()
        assertEquals(listOf("second"), completed)
    }
}
