package dev.anilbeesetti.nextplayer.core.media.network.keys

import android.net.Uri
import java.io.File

data class StagedSshKey(
    val fileName: String,
    val displayName: String,
)

interface SshKeyStore {
    suspend fun stage(uri: Uri): StagedSshKey
    fun resolve(fileName: String): File
    suspend fun commit(fileName: String): String
    suspend fun delete(fileName: String)
}
