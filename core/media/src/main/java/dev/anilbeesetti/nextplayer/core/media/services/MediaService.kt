package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity

interface MediaService {
    fun initialize(activity: ComponentActivity)
    suspend fun deleteMedia(uris: List<Uri>): Boolean
    suspend fun renameMedia(uri: Uri, to: String): Boolean
    suspend fun shareMedia(uris: List<Uri>)
    suspend fun hideVideos(uris: List<Uri>): Boolean

    /**
     * Moves files from the vault back to the given [destinationDir] (defaults to Movies).
     * [filenames] are relative to the vault directory.
     */
    suspend fun unhideVideos(filenames: List<String>, destinationDir: String? = null): Boolean

    /**
     * Returns the list of filenames currently stored in the vault directory.
     */
    fun listVaultFiles(): List<String>

    companion object {
        fun willSystemAsksForDeleteConfirmation(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        }
    }
}
