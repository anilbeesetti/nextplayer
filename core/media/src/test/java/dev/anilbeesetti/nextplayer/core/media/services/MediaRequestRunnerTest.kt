package dev.anilbeesetti.nextplayer.core.media.services

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRequestRunnerTest {

    @Test
    fun `splits requests at the platform uri limit`() = runBlocking {
        val requestedBatchSizes = mutableListOf<Int>()

        val result = runMediaRequests(
            items = (1..2_001).toList(),
            maxBatchSize = 2_000,
            itemExists = { true },
            request = { batch ->
                require(batch.size <= 2_000)
                requestedBatchSizes += batch.size
                true
            },
        )

        assertTrue(result)
        assertEquals(listOf(2_000, 1), requestedBatchSizes)
    }

    @Test
    fun `retries without an item that disappeared before request creation`() = runBlocking {
        val requestedBatches = mutableListOf<List<String>>()

        val result = runMediaRequests(
            items = listOf("existing", "stale"),
            maxBatchSize = 2_000,
            itemExists = { it == "existing" },
            request = { batch ->
                requestedBatches += batch
                if ("stale" in batch) throw IllegalArgumentException("URI no longer exists")
                true
            },
        )

        assertTrue(result)
        assertEquals(
            listOf(
                listOf("existing", "stale"),
                listOf("existing"),
            ),
            requestedBatches,
        )
    }

    @Test
    fun `treats items already deleted by another app as success`() = runBlocking {
        var requestCount = 0

        val result = runMediaRequests(
            items = listOf("stale"),
            maxBatchSize = 2_000,
            itemExists = { false },
            request = {
                requestCount++
                throw IllegalArgumentException("URI no longer exists")
            },
        )

        assertTrue(result)
        assertEquals(1, requestCount)
    }

    @Test
    fun `returns failure when illegal argument is not caused by stale items`() = runBlocking {
        var requestCount = 0

        val result = runMediaRequests(
            items = listOf("existing"),
            maxBatchSize = 2_000,
            itemExists = { true },
            request = {
                requestCount++
                throw IllegalArgumentException("Unrelated provider rejection")
            },
        )

        assertFalse(result)
        assertEquals(1, requestCount)
    }
}
