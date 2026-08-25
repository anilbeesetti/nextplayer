package dev.anilbeesetti.nextplayer.core.media.network.keys

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch

class SshKeyFiles(
    private val stagingDirectory: File,
    private val committedDirectory: File,
    private val fileName: () -> String = { "${UUID.randomUUID()}.key" },
    reconciliationRequired: Boolean = false,
) {

    private val lifecycleLock = Any()
    private val reconciliationComplete = CountDownLatch(if (reconciliationRequired) 1 else 0)
    private val importsInProgress = mutableSetOf<String>()

    /**
     * Removes keys left behind by an interrupted save or delete. Passing null only releases the
     * barrier: without a trustworthy database snapshot, deleting any key would risk data loss.
     */
    fun initialize(referencedFileNames: Set<String>?) {
        synchronized(lifecycleLock) {
            if (reconciliationComplete.count == 0L) return
            try {
                referencedFileNames?.let(::reconcile)
            } finally {
                reconciliationComplete.countDown()
            }
        }
    }

    fun stage(inputStream: InputStream): String {
        awaitReconciliation()
        val pendingImport = synchronized(lifecycleLock, ::reserveImport)
        try {
            inputStream.use { input ->
                pendingImport.temporaryFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        bytesCopied += read
                        if (bytesCopied > MAX_PRIVATE_KEY_BYTES) {
                            throw IOException("Private key exceeds 1 MiB")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            synchronized(lifecycleLock) {
                val stagedFile = stagedFile(pendingImport.fileName)
                check(!stagedFile.exists() && !committedFile(pendingImport.fileName).exists()) {
                    "Private key filename is already in use"
                }
                check(pendingImport.temporaryFile.renameTo(stagedFile)) {
                    "Couldn't publish private key"
                }
                importsInProgress -= pendingImport.fileName
            }
        } catch (throwable: Throwable) {
            synchronized(lifecycleLock) {
                importsInProgress -= pendingImport.fileName
                if (!pendingImport.temporaryFile.deleteIfPresent()) {
                    throwable.addSuppressed(IOException("Couldn't delete partial private key"))
                }
            }
            throw throwable
        }
        return pendingImport.fileName
    }

    fun resolve(fileName: String): File {
        awaitReconciliation()
        return synchronized(lifecycleLock) {
            val staged = stagedFile(fileName)
            if (staged.isFile) return@synchronized staged

            val committed = committedFile(fileName)
            if (committed.isFile) return@synchronized committed

            throw FileNotFoundException("Private key is missing")
        }
    }

    fun commit(fileName: String): String {
        awaitReconciliation()
        return synchronized(lifecycleLock) {
            val source = stagedFile(fileName)
            require(source.isFile) { "Private key is missing" }
            committedDirectory.mkdirs()
            val target = committedFile(fileName)
            check(source.renameTo(target)) { "Couldn't commit private key" }
            fileName
        }
    }

    fun delete(fileName: String) {
        awaitReconciliation()
        synchronized(lifecycleLock) {
            val stagedDeleted = stagedFile(fileName).deleteIfPresent()
            val committedDeleted = committedFile(fileName).deleteIfPresent()
            check(stagedDeleted && committedDeleted) { "Couldn't delete private key" }
        }
    }

    private fun reconcile(referencedFileNames: Set<String>) {
        val stagedDeleted = stagingDirectory.listFiles().orEmpty()
            .map { file -> file.deleteIfPresent() }
            .all { deleted -> deleted }
        val committedDeleted = committedDirectory.listFiles().orEmpty()
            .filterNot { it.name in referencedFileNames }
            .map { file -> file.deleteIfPresent() }
            .all { deleted -> deleted }
        check(stagedDeleted && committedDeleted) { "Couldn't reconcile private keys" }
    }

    private fun reserveImport(): PendingImport {
        val generatedName = fileName().also(::requireValidName)
        check(
            generatedName !in importsInProgress &&
                !stagedFile(generatedName).exists() &&
                !committedFile(generatedName).exists(),
        ) { "Private key filename is already in use" }
        stagingDirectory.mkdirs()
        importsInProgress += generatedName
        return PendingImport(
            fileName = generatedName,
            temporaryFile = File(stagingDirectory, ".$generatedName.importing"),
        )
    }

    private fun awaitReconciliation() = reconciliationComplete.await()

    private fun stagedFile(fileName: String) = File(stagingDirectory, validated(fileName))

    private fun committedFile(fileName: String) = File(committedDirectory, validated(fileName))

    private fun File.deleteIfPresent() = !exists() || delete() || !exists()

    private fun validated(fileName: String) = fileName.also(::requireValidName)

    private fun requireValidName(fileName: String) {
        require(SshKeyStore.isValidFileName(fileName)) { "Invalid private-key filename" }
    }

    private companion object {
        const val MAX_PRIVATE_KEY_BYTES = 1024L * 1024L
    }

    private data class PendingImport(
        val fileName: String,
        val temporaryFile: File,
    )
}
