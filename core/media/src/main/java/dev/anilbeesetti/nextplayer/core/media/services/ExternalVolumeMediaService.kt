package dev.anilbeesetti.nextplayer.core.media.services

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import dev.anilbeesetti.nextplayer.core.common.extensions.hasAllFilesAccess
import dev.anilbeesetti.nextplayer.core.common.extensions.restartApp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * Finds videos on mounted external drives (USB OTG drives, SD cards) by reading their files
 * directly.
 *
 * MediaStore does not list some drives (for example a USB drive on a Pixel), so videos on them
 * never show up. Reading `/storage/<uuid>` needs the "All files access" permission; without it
 * this service reports nothing.
 */
@Single
class ExternalVolumeMediaService(private val context: Context) {

    private val refreshCount = MutableStateFlow(value = 0)

    /** Emits whenever a refresh is requested, so listeners read the drives again. */
    val changes: Flow<Int> = refreshCount

    fun refresh() {
        refreshCount.update { it + 1 }
    }

    init {
        watchAllFilesAccess()
    }

    // "All files access" is switched on or off on a system page, so no screen of the app sees the
    // change. Check every time an activity comes back to the front and read the drives again.
    private fun watchAllFilesAccess() {
        val application = context.applicationContext as? Application ?: return
        var wasGranted = hasAllFilesAccess()
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    val isGranted = hasAllFilesAccess()
                    if (isGranted == wasGranted) return
                    wasGranted = isGranted
                    refresh()
                    // Android can keep showing a drive as unreadable to a running app until the
                    // app is restarted. Restart once when that happens right after granting.
                    if (isGranted && hasUnreadableMountedVolume()) application.restartApp()
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityPaused(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {}
            },
        )
    }

    private fun hasUnreadableMountedVolume(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return context.getSystemService(StorageManager::class.java).storageVolumes
            .filter { !it.isPrimary && it.state == Environment.MEDIA_MOUNTED }
            .any { it.directory?.canRead() != true }
    }

    suspend fun fetchVideos(folderPath: String? = null): List<MediaVideo> =
        withContext(Dispatchers.IO) {
            volumes()
                .flatMap { volume ->
                    findVideoFiles(volume.directory).map { it.toMediaVideo(volume) }
                }
                .filter { folderPath == null || it.path.startsWith("$folderPath/") }
        }

    suspend fun findVideo(uri: Uri): MediaVideo? = withContext(Dispatchers.IO) {
        val file = uri.takeIf { it.scheme == "file" }?.path?.let(::File) ?: return@withContext null
        val volume = volumes().firstOrNull { file.path.startsWith("${it.directory.path}/") }
        if (volume == null || !file.isFile) null else file.toMediaVideo(volume)
    }

    private class ExternalVolume(val directory: File, val uuid: String)

    // The folder Android reports for the drive (on a Pixel USB drive it is /mnt/media_rw/<uuid>,
    // not /storage/<uuid>) is the only path apps can read.
    private fun volumes(): List<ExternalVolume> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        if (!Environment.isExternalStorageManager()) return emptyList()
        return context.getSystemService(StorageManager::class.java).storageVolumes
            .filter { !it.isPrimary && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { volume ->
                val directory = volume.directory
                val uuid = volume.uuid
                if (directory != null && uuid != null && directory.canRead()) {
                    ExternalVolume(
                        directory,
                        uuid,
                    )
                } else {
                    null
                }
            }
    }

    private fun File.toMediaVideo(volume: ExternalVolume): MediaVideo {
        val modifiedSeconds = lastModified() / 1000
        val displayPath = volumeDisplayPath(
            volume.directory,
            volume.uuid,
            this,
        )
        return MediaVideo(
            // MediaStore ids are positive; keep these negative so the two never collide.
            id = -(displayPath.hashCode().toLong() and 0x7fffffffL) - 1,
            uri = Uri.fromFile(this),
            path = displayPath,
            title = name,
            parentPath = displayPath.substringBeforeLast(
                '/',
                missingDelimiterValue = "/",
            ),
            displayName = name,
            // Probing every file on a slow drive would delay the list; the player reads it later.
            duration = 0,
            size = length(),
            width = 0,
            height = 0,
            dateModified = modifiedSeconds,
            dateAdded = modifiedSeconds,
        )
    }
}

private val VIDEO_EXTENSIONS = setOf(
    "mp4",
    "m4v",
    "mkv",
    "webm",
    "avi",
    "mov",
    "wmv",
    "flv",
    "3gp",
    "mpg",
    "mpeg",
    "ts",
    "m2ts",
)

/** Video files under [root], skipping hidden folders such as `.Trash`. */
internal fun findVideoFiles(root: File): List<File> = root.walkTopDown()
    .onEnter { !it.name.startsWith(".") }
    .filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
    .toList()

/**
 * The `/storage/<uuid>/...` path the folder list groups a drive's files by, for a [file] that is
 * read from the drive's real [directory].
 */
internal fun volumeDisplayPath(directory: File, uuid: String, file: File): String =
    "/storage/$uuid/" + file.relativeTo(directory).invariantSeparatorsPath
