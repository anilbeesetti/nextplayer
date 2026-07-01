package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.ChecksSdkIntAtLeast
import java.io.File

interface MediaOperationsService {
    fun initialize(activity: ComponentActivity)
    suspend fun deleteMedia(uris: List<Uri>): Boolean
    suspend fun renameMedia(uri: Uri, to: String): Boolean
    suspend fun shareMedia(uris: List<Uri>)
    suspend fun moveMedia(uris: List<Uri>, targetDir: File): Map<Uri, File?>

    companion object {
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
        fun willSystemAsksForDeleteConfirmation(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        }
    }
}