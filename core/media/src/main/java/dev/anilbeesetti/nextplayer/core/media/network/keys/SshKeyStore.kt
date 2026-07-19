package dev.anilbeesetti.nextplayer.core.media.network.keys

import android.net.Uri
import java.io.File

data class StagedSshKey(
    val fileName: String,
    val displayName: String,
)

interface SshKeyStore {
    /**
     * Releases the startup barrier after removing files not referenced by persisted connections.
     * A null set means persistence could not be enumerated, so no files may be removed.
     */
    suspend fun initialize(referencedFileNames: Set<String>?) = Unit

    suspend fun stage(uri: Uri): StagedSshKey
    fun resolve(fileName: String): File
    suspend fun commit(fileName: String): String
    suspend fun delete(fileName: String)

    companion object {
        private val VALID_FILE_NAME = Regex("[0-9a-fA-F-]+\\.key")

        fun isValidFileName(fileName: String): Boolean = VALID_FILE_NAME.matches(fileName)
    }
}
