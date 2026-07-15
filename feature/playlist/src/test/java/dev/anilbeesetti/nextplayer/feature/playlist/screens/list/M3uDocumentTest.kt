package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class M3uDocumentTest {
    @Test
    fun delayedFileResultDoesNotReplaceNewerEmptyInteraction() = runTest {
        val delayedResult = CompletableDeferred<M3uDocument?>()
        val coordinator = PlaylistCreationCoordinator(this) {}
        var request: M3uFileRequest? = null
        coordinator.chooseFile { request = it }
        coordinator.prepareFile(checkNotNull(request)) {
            withContext(NonCancellable) { delayedResult.await() }
        }
        runCurrent()

        coordinator.chooseEmpty()
        delayedResult.complete(M3uDocument("content://documents/old.m3u", "Old.m3u"))
        advanceUntilIdle()

        assertEquals(CreationDialog.EMPTY, coordinator.dialog)
        assertNull(coordinator.fileDocument)
    }

    @Test
    fun delayedFileFailureDoesNotReportErrorAfterNewerUrlInteraction() = runTest {
        val delayedResult = CompletableDeferred<M3uDocument?>()
        var errorCount = 0
        val coordinator = PlaylistCreationCoordinator(this) { errorCount++ }
        var request: M3uFileRequest? = null
        coordinator.chooseFile { request = it }
        coordinator.prepareFile(checkNotNull(request)) {
            withContext(NonCancellable) { delayedResult.await() }
        }
        runCurrent()

        coordinator.chooseUrl()
        delayedResult.complete(null)
        advanceUntilIdle()

        assertEquals(CreationDialog.URL, coordinator.dialog)
        assertEquals(0, errorCount)
    }

    @Test
    fun onlyLatestSuccessfulFileRequestOpensFileDialog() = runTest {
        val oldResult = CompletableDeferred<M3uDocument?>()
        val latestResult = CompletableDeferred<M3uDocument?>()
        val coordinator = PlaylistCreationCoordinator(this) {}
        var oldRequest: M3uFileRequest? = null
        coordinator.chooseFile { oldRequest = it }
        coordinator.prepareFile(checkNotNull(oldRequest)) {
            withContext(NonCancellable) { oldResult.await() }
        }
        runCurrent()

        var latestRequest: M3uFileRequest? = null
        coordinator.chooseFile { latestRequest = it }
        coordinator.prepareFile(checkNotNull(latestRequest)) { latestResult.await() }
        runCurrent()
        oldResult.complete(M3uDocument("content://documents/old.m3u", "Old.m3u"))
        advanceUntilIdle()

        assertEquals(CreationDialog.NONE, coordinator.dialog)
        assertNull(coordinator.fileDocument)

        val latestDocument = M3uDocument("content://documents/latest.m3u", "Latest.m3u")
        latestResult.complete(latestDocument)
        advanceUntilIdle()

        assertEquals(CreationDialog.FILE, coordinator.dialog)
        assertEquals(latestDocument, coordinator.fileDocument)
    }

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
