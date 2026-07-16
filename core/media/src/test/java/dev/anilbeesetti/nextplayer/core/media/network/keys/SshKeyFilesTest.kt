package dev.anilbeesetti.nextplayer.core.media.network.keys

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
        val staged = store.stage("staged".byteInputStream())

        assertEquals("staged", store.resolve(staged).readText())
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
    fun `stage surfaces partial cleanup failure without masking import error`() {
        File(stagingDirectory(), "feed.key").apply {
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
            assertTrue(stagedFile.isFile)
            import.cancel()
            allowImportToReturn.countDown()
            import.join()

            assertTrue(import.isCancelled)
            assertFalse(stagedFile.exists())
        } finally {
            allowImportToReturn.countDown()
            dispatcher.close()
        }
    }

    private fun createStore() = SshKeyFiles(
        stagingDirectory = stagingDirectory(),
        committedDirectory = committedDirectory(),
        fileName = { "feed.key" },
    )

    private fun stagingDirectory() = File(temporaryFolder.root, "staging")

    private fun committedDirectory() = File(temporaryFolder.root, "committed")
}
