package dev.anilbeesetti.nextplayer.core.media.network.keys

import java.io.File
import java.io.FileNotFoundException
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

    private fun createStore() = SshKeyFiles(
        stagingDirectory = stagingDirectory(),
        committedDirectory = committedDirectory(),
        fileName = { "feed.key" },
    )

    private fun stagingDirectory() = File(temporaryFolder.root, "staging")

    private fun committedDirectory() = File(temporaryFolder.root, "committed")
}
