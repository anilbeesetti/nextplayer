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

    /**
     * Copies (or moves, when [mode] is [TransferMode.MOVE]) the given [uris] into the
     * user-picked SAF folder [treeUri]. Progress is reported per-file via [onProgress].
     *
     * On [TransferMode.MOVE], the originals are deleted once copied — on Android R+ this
     * surfaces the system delete-confirmation dialog for files the app doesn't own.
     */
    suspend fun transferMedia(
        uris: List<Uri>,
        treeUri: Uri,
        mode: TransferMode,
        onProgress: (TransferProgress) -> Unit,
    ): TransferResult

    companion object {
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
        fun willSystemAsksForDeleteConfirmation(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        }
    }
}

enum class TransferMode { COPY, MOVE }

/** Per-file progress of a [MediaOperationsService.transferMedia] operation. */
data class TransferProgress(
    val completed: Int,
    val total: Int,
    val currentName: String?,
)

/** Outcome of a [MediaOperationsService.transferMedia] operation. */
data class TransferResult(
    val succeeded: Int,
    val failed: Int,
)