package dev.anilbeesetti.nextplayer.core.media.network.keys

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.UUID

class SshKeyFiles(
    private val stagingDirectory: File,
    private val committedDirectory: File,
    private val fileName: () -> String = { "${UUID.randomUUID()}.key" },
) {

    fun stage(inputStream: InputStream): String {
        val generatedName = fileName().also(::requireValidName)
        stagingDirectory.mkdirs()
        inputStream.use { input ->
            stagedFile(generatedName).outputStream().use(input::copyTo)
        }
        return generatedName
    }

    fun resolve(fileName: String): File {
        val staged = stagedFile(fileName)
        if (staged.isFile) return staged

        val committed = committedFile(fileName)
        if (committed.isFile) return committed

        throw FileNotFoundException("Private key is missing")
    }

    fun commit(fileName: String): String {
        val source = stagedFile(fileName)
        require(source.isFile) { "Private key is missing" }
        committedDirectory.mkdirs()
        val target = committedFile(fileName)
        check(source.renameTo(target)) { "Couldn't commit private key" }
        return fileName
    }

    fun delete(fileName: String) {
        stagedFile(fileName).delete()
        committedFile(fileName).delete()
    }

    private fun stagedFile(fileName: String) = File(stagingDirectory, validated(fileName))

    private fun committedFile(fileName: String) = File(committedDirectory, validated(fileName))

    private fun validated(fileName: String) = fileName.also(::requireValidName)

    private fun requireValidName(fileName: String) {
        require(VALID_NAME.matches(fileName)) { "Invalid private-key filename" }
    }

    private companion object {
        val VALID_NAME = Regex("[0-9a-fA-F-]+\\.key")
    }
}
