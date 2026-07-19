package dev.anilbeesetti.nextplayer.core.media.network.keys

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SshKeyFilesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stage commit resolve and delete preserve bytes`() {
        val store = createStore()

        val staged = store.stage("private-key".byteInputStream())

        assertEquals("feed.key", staged)
        assertEquals("private-key", store.resolve(staged).readText())

        val committed = store.commit(staged)

        assertEquals("feed.key", committed)
        assertEquals("private-key", store.resolve(committed).readText())
        store.delete(committed)
        assertFalse(File(committedDirectory(), committed).exists())
    }

    @Test
    fun `resolve prefers staged key over committed key`() {
        val store = createStore()
        committedDirectory().mkdirs()
        File(committedDirectory(), "feed.key").writeText("committed")
        File(stagingDirectory(), "feed.key").apply {
            parentFile?.mkdirs()
            writeText("staged")
        }

        assertEquals("staged", store.resolve("feed.key").readText())
    }

    @Test
    fun `resolve missing key throws specific error`() {
        val exception = assertThrows(FileNotFoundException::class.java) {
            createStore().resolve("feed.key")
        }

        assertEquals("Private key is missing", exception.message)
    }

    @Test
    fun `generated filename rejects traversal and malformed names`() {
        val store = createStore()

        listOf("../secret", "fixed.pem", "fixed.key/other", "fixed key.key").forEach { fileName ->
            assertThrows(IllegalArgumentException::class.java) {
                store.resolve(fileName)
            }
        }
    }

    @Test
    fun `delete removes staged and committed copies`() {
        val store = createStore()
        val staged = store.stage("staged".byteInputStream())
        committedDirectory().mkdirs()
        File(committedDirectory(), staged).writeText("committed")

        store.delete(staged)

        assertFalse(File(stagingDirectory(), staged).exists())
        assertFalse(File(committedDirectory(), staged).exists())
        assertTrue(stagingDirectory().isDirectory)
    }

    @Test
    fun `stage failure removes partial key file`() {
        val store = createStore()
        val failingInput = object : InputStream() {
            private var byteIndex = 0

            override fun read(): Int = when (byteIndex++) {
                0 -> 'k'.code
                1 -> 'e'.code
                else -> throw IOException("Import failed")
            }
        }

        assertThrows(IOException::class.java) {
            store.stage(failingInput)
        }

        assertFalse(File(stagingDirectory(), "feed.key").exists())
    }

    @Test
    fun `stage rejects key larger than one mebibyte and removes partial file`() {
        val exception = assertThrows(IOException::class.java) {
            createStore().stage(ByteArray(1024 * 1024 + 1).inputStream())
        }

        assertEquals("Private key exceeds 1 MiB", exception.message)
        assertFalse(File(stagingDirectory(), "feed.key").exists())
    }

    @Test
    fun `stage accepts key exactly one mebibyte`() {
        val store = createStore()

        val staged = store.stage(ByteArray(1024 * 1024).inputStream())

        assertEquals(1024L * 1024L, store.resolve(staged).length())
    }

    @Test
    fun `startup reconciliation removes staged and unreferenced committed keys`() {
        File(stagingDirectory(), "staged.key").apply {
            parentFile?.mkdirs()
            writeText("staged")
        }
        File(committedDirectory(), "keep.key").apply {
            parentFile?.mkdirs()
            writeText("keep")
        }
        File(committedDirectory(), "orphan.key").writeText("orphan")

        createStore(reconciliationRequired = true).initialize(setOf("keep.key"))

        assertFalse(File(stagingDirectory(), "staged.key").exists())
        assertTrue(File(committedDirectory(), "keep.key").isFile)
        assertFalse(File(committedDirectory(), "orphan.key").exists())
    }

    @Test
    fun `failed database enumeration releases barrier without deleting keys`() {
        File(stagingDirectory(), "staged.key").apply {
            parentFile?.mkdirs()
            writeText("staged")
        }
        File(committedDirectory(), "committed.key").apply {
            parentFile?.mkdirs()
            writeText("committed")
        }
        val store = createStore(reconciliationRequired = true)

        store.initialize(null)

        assertTrue(File(stagingDirectory(), "staged.key").isFile)
        assertTrue(File(committedDirectory(), "committed.key").isFile)
        assertEquals("new", store.stage("new".byteInputStream()).let(store::resolve).readText())
    }

    @Test
    fun `key operations wait until startup reconciliation completes`() {
        val store = createStore(reconciliationRequired = true)
        val stageStarted = CountDownLatch(1)
        val stageFinished = CountDownLatch(1)
        val stageSucceeded = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()

        try {
            executor.execute {
                stageStarted.countDown()
                store.stage("new".byteInputStream())
                stageSucceeded.set(true)
                stageFinished.countDown()
            }

            assertTrue(stageStarted.await(5, TimeUnit.SECONDS))
            assertFalse(stageFinished.await(100, TimeUnit.MILLISECONDS))

            store.initialize(emptySet())

            assertTrue(stageFinished.await(5, TimeUnit.SECONDS))
            assertTrue(stageSucceeded.get())
            assertEquals("new", store.resolve("feed.key").readText())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `blocked import does not prevent resolving committed key`() {
        committedDirectory().mkdirs()
        File(committedDirectory(), "cafe.key").writeText("committed")
        val importStarted = CountDownLatch(1)
        val allowImport = CountDownLatch(1)
        val blockingInput = object : InputStream() {
            override fun read(): Int {
                importStarted.countDown()
                allowImport.await()
                return -1
            }
        }
        val store = createStore()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val import = executor.submit<String> { store.stage(blockingInput) }
            assertTrue(importStarted.await(5, TimeUnit.SECONDS))

            val resolved = executor.submit<String> {
                store.resolve("cafe.key").readText()
            }

            try {
                assertEquals("committed", resolved.get(1, TimeUnit.SECONDS))
            } catch (error: TimeoutException) {
                throw AssertionError("Resolving a committed key waited for the import stream", error)
            }
            allowImport.countDown()
            assertEquals("feed.key", import.get(5, TimeUnit.SECONDS))
        } finally {
            allowImport.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `key being imported is not resolvable until import completes`() {
        val importStarted = CountDownLatch(1)
        val allowImport = CountDownLatch(1)
        val blockingInput = object : InputStream() {
            override fun read(): Int {
                importStarted.countDown()
                allowImport.await()
                return -1
            }
        }
        val store = createStore()
        val executor = Executors.newSingleThreadExecutor()

        try {
            val import = executor.submit<String> { store.stage(blockingInput) }
            assertTrue(importStarted.await(5, TimeUnit.SECONDS))

            assertThrows(FileNotFoundException::class.java) {
                store.resolve("feed.key")
            }

            allowImport.countDown()
            assertEquals("feed.key", import.get(5, TimeUnit.SECONDS))
            assertTrue(store.resolve("feed.key").isFile)
        } finally {
            allowImport.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `stage rejects generated filename collision without changing existing key`() {
        val existing = File(stagingDirectory(), "feed.key").apply {
            parentFile?.mkdirs()
            writeText("existing")
        }

        val exception = assertThrows(IllegalStateException::class.java) {
            createStore().stage("replacement".byteInputStream())
        }

        assertEquals("Private key filename is already in use", exception.message)
        assertEquals("existing", existing.readText())
    }

    @Test
    fun `stage surfaces partial cleanup failure without masking import error`() {
        File(stagingDirectory(), ".feed.key.importing").apply {
            mkdirs()
            resolve("undeletable").writeText("content")
        }

        val exception = assertThrows(IOException::class.java) {
            createStore().stage("private-key".byteInputStream())
        }

        assertEquals("Couldn't delete partial private key", exception.suppressed.single().message)
    }

    @Test
    fun `delete missing key is idempotent`() {
        createStore().delete("feed.key")

        assertFalse(File(stagingDirectory(), "feed.key").exists())
        assertFalse(File(committedDirectory(), "feed.key").exists())
    }

    @Test
    fun `delete failure still attempts both lifecycle copies`() {
        val staged = File(stagingDirectory(), "feed.key").apply {
            mkdirs()
            resolve("undeletable").writeText("content")
        }
        val committed = File(committedDirectory(), "feed.key").apply {
            parentFile?.mkdirs()
            writeText("committed")
        }

        val exception = assertThrows(IllegalStateException::class.java) {
            createStore().delete("feed.key")
        }

        assertEquals("Couldn't delete private key", exception.message)
        assertTrue(staged.exists())
        assertFalse(committed.exists())
    }

    @Test
    fun `cancelled import removes key completed by blocking copy`() = runBlocking {
        val copyCompleted = CountDownLatch(1)
        val allowImportToReturn = CountDownLatch(1)
        val inputStream = object : ByteArrayInputStream("private-key".toByteArray()) {
            override fun close() {
                copyCompleted.countDown()
                allowImportToReturn.await()
                super.close()
            }
        }
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val stagedFile = File(stagingDirectory(), "feed.key")
        val importingFile = File(stagingDirectory(), ".feed.key.importing")

        try {
            val import = launch(Dispatchers.Default) {
                stageSshKey(
                    keyFiles = createStore(),
                    ioDispatcher = dispatcher,
                    displayName = { "id_rsa" },
                    inputStream = { inputStream },
                )
            }

            assertTrue(copyCompleted.await(5, TimeUnit.SECONDS))
            assertTrue(importingFile.isFile)
            assertFalse(stagedFile.exists())
            import.cancel()
            allowImportToReturn.countDown()
            import.join()

            assertTrue(import.isCancelled)
            assertFalse(importingFile.exists())
            assertFalse(stagedFile.exists())
        } finally {
            allowImportToReturn.countDown()
            dispatcher.close()
        }
    }

    private fun createStore(reconciliationRequired: Boolean = false) = SshKeyFiles(
        stagingDirectory = stagingDirectory(),
        committedDirectory = committedDirectory(),
        fileName = { "feed.key" },
        reconciliationRequired = reconciliationRequired,
    )

    private fun stagingDirectory() = File(temporaryFolder.root, "staging")

    private fun committedDirectory() = File(temporaryFolder.root, "committed")
}
