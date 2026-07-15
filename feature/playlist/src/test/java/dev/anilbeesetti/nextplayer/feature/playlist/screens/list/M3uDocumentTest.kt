package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uDocumentTest {
    @Test
    fun failedPersistablePermissionWithExistingReadableGrantCreatesDocument() = runTest {
        val preparer = M3uDocumentPreparer(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            persistPermission = { throw SecurityException("Permission denied") },
            hasPersistedReadPermission = { true },
            queryDisplayName = { "News.m3u" },
        )
        val document = preparer.prepare(
            uri = "content://documents/news.m3u",
            fallbackDisplayName = "news.m3u",
        )

        assertEquals(M3uDocument("content://documents/news.m3u", "News.m3u"), document)
    }

    @Test
    fun providerMetadataPreparationUsesInjectedDispatcher() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "m3u-document-io")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            var queryThreadName: String? = null
            val preparer = M3uDocumentPreparer(
                ioDispatcher = dispatcher,
                persistPermission = {},
                hasPersistedReadPermission = { false },
                queryDisplayName = {
                    queryThreadName = Thread.currentThread().name
                    "News.m3u"
                },
            )

            preparer.prepare(
                uri = "content://documents/news.m3u",
                fallbackDisplayName = "news.m3u",
            )

            assertEquals("m3u-document-io", queryThreadName)
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun failedPersistablePermissionWithoutExistingReadableGrantDoesNotCreateDocument() = runTest {
        val preparer = M3uDocumentPreparer(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            persistPermission = { throw SecurityException("Permission denied") },
            hasPersistedReadPermission = { false },
            queryDisplayName = { "News.m3u" },
        )
        val document = preparer.prepare(
            uri = "content://documents/news.m3u",
            fallbackDisplayName = "news.m3u",
        )

        assertNull(document)
    }

    @Test
    fun persistedDocumentUsesFallbackWhenDisplayNameQueryFails() = runTest {
        val preparer = M3uDocumentPreparer(
            ioDispatcher = StandardTestDispatcher(testScheduler),
            persistPermission = {},
            hasPersistedReadPermission = { false },
            queryDisplayName = { error("Provider failure") },
        )
        val document = preparer.prepare(
            uri = "content://documents/news.m3u",
            fallbackDisplayName = "news.m3u",
        )

        assertEquals(M3uDocument("content://documents/news.m3u", "news.m3u"), document)
    }
}
