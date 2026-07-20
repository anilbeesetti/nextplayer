package dev.anilbeesetti.nextplayer.core.media.services

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalMediaOperationsServiceTest {

    @Test
    fun moveMediaDoesNotOverwriteAnExistingVaultFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testRoot = File(context.cacheDir, "issue-1828-${UUID.randomUUID()}")
        val sourceDir = File(testRoot, "source").apply { mkdirs() }
        val targetDir = File(testRoot, "vault").apply { mkdirs() }
        val sourceFile = File(sourceDir, "duplicate.mp4").apply { writeText("new source") }
        val existingVaultFile = File(targetDir, sourceFile.name).apply { writeText("existing vault video") }
        val sourceUri = sourceFile.toUri()

        try {
            val moved = LocalMediaOperationsService(context).moveMedia(mapOf(sourceUri to existingVaultFile))

            assertNull("A colliding source must be reported as not moved", moved[sourceUri])
            assertTrue("The colliding source must remain in its original location", sourceFile.exists())
            assertEquals("existing vault video", existingVaultFile.readText())
        } finally {
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun concurrentMovesToOneDestinationPreserveTheLosingSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testRoot = File(context.cacheDir, "issue-1828-race-${UUID.randomUUID()}")
        val sourceFiles = listOf("first video", "second video").mapIndexed { index, content ->
            File(File(testRoot, "source-$index").apply { mkdirs() }, "duplicate.mp4")
                .apply { writeText(content) }
        }
        val destination = File(File(testRoot, "vault").apply { mkdirs() }, "shared.mp4")
        val barrier = CyclicBarrier(sourceFiles.size)
        val service = LocalMediaOperationsService(context)

        try {
            val results = coroutineScope {
                sourceFiles.map { source ->
                    async(Dispatchers.Default) {
                        barrier.await()
                        val uri = source.toUri()
                        service.moveMedia(mapOf(uri to destination))[uri]
                    }
                }.awaitAll()
            }

            assertEquals(1, results.count { it != null })
            assertEquals("Exactly one losing source must remain", 1, sourceFiles.count { it.exists() })
            assertTrue(destination.readText() in setOf("first video", "second video"))
        } finally {
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun cancellationDuringCopyPreservesSourceAndRemovesPartialDestination() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testRoot = File(context.cacheDir, "issue-1828-cancel-${UUID.randomUUID()}")
        val source = File(File(testRoot, "source").apply { mkdirs() }, "large.mp4")
            .apply { writeBytes(ByteArray(DEFAULT_BUFFER_SIZE * 4) { it.toByte() }) }
        val destination = File(File(testRoot, "vault").apply { mkdirs() }, "unique.mp4")
        var cancellationChecks = 0

        try {
            assertThrows(CancellationException::class.java) {
                LocalMediaOperationsService(context).moveFileWithoutOverwrite(source, destination) {
                    cancellationChecks += 1
                    if (cancellationChecks == 2) throw CancellationException("cancel during copy")
                }
            }

            assertTrue("Cancellation must leave the source video untouched", source.exists())
            assertEquals(DEFAULT_BUFFER_SIZE * 4L, source.length())
            assertTrue("A partial vault copy must be removed", !destination.exists())
        } finally {
            testRoot.deleteRecursively()
        }
    }

    @Test
    fun cancellationAfterCommittedMovePreservesRemainingSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testRoot = File(context.cacheDir, "issue-1828-batch-cancel-${UUID.randomUUID()}")
        val firstSource = File(File(testRoot, "source-1").apply { mkdirs() }, "first.mp4")
            .apply { writeText("first video") }
        val secondSource = File(File(testRoot, "source-2").apply { mkdirs() }, "second.mp4")
            .apply { writeBytes(ByteArray(64 * 1024 * 1024)) }
        val vaultDir = File(testRoot, "vault").apply { mkdirs() }
        val firstDestination = File(vaultDir, "first.mp4")
        val secondDestination = File(vaultDir, "second.mp4")
        val service = LocalMediaOperationsService(context)

        try {
            val moveJob = launch(Dispatchers.Default) {
                service.moveMedia(
                    linkedMapOf(
                        firstSource.toUri() to firstDestination,
                        secondSource.toUri() to secondDestination,
                    ),
                )
            }
            while (firstSource.exists()) yield()
            moveJob.cancelAndJoin()

            assertTrue("The committed first vault copy must remain", firstDestination.exists())
            assertTrue("The uncommitted second source must remain", secondSource.exists())
            assertTrue("A cancelled second vault copy must be removed", !secondDestination.exists())
        } finally {
            testRoot.deleteRecursively()
        }
    }
}
