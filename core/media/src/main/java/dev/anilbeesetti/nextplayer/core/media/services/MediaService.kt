package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.flow.Flow

/**
 * Service for accessing media (videos and folders) from the device's storage.
 *
 * This service provides both reactive (Flow-based) and one-shot methods for
 * fetching media. The reactive methods automatically emit new values when
 * the underlying media changes (e.g., files added/removed).
 */
interface MediaService {

    /**
     * Observes all unique folders containing videos under the given path.
     * Emits a new list whenever the underlying media changes.
     *
     * @param folderPath The root path to search for videos. Defaults to external storage root.
     * @return A flow of folder lists. Each folder represents a directory containing at least one video.
     *         Folder statistics (videosCount, foldersCount) are set to 0 - compute at use case layer.
     */
    fun observeFolders(
        folderPath: String = Environment.getExternalStorageDirectory().path,
    ): Flow<List<MediaFolder>>

    /**
     * Observes all videos under the given path recursively.
     * Emits a new list whenever the underlying media changes.
     *
     * @param folderPath The root path to search for videos. Defaults to external storage root.
     * @return A flow of video lists containing all videos found under the path.
     */
    fun observeVideos(
        folderPath: String = Environment.getExternalStorageDirectory().path,
    ): Flow<List<MediaVideo>>

    /**
     * Fetches all unique folders containing videos under the given path (one-shot).
     *
     * @param folderPath The root path to search for videos. Defaults to external storage root.
     * @return List of folders, each representing a directory containing at least one video.
     *         Folder statistics (videosCount, foldersCount) are set to 0 - compute at use case layer.
     */
    suspend fun fetchFolders(
        folderPath: String = Environment.getExternalStorageDirectory().path,
    ): List<MediaFolder>

    /**
     * Fetches all videos under the given path recursively (one-shot).
     *
     * @param folderPath The root path to search for videos. Defaults to external storage root.
     * @return List of all videos found under the path.
     */
    suspend fun fetchVideos(
        folderPath: String = Environment.getExternalStorageDirectory().path,
    ): List<MediaVideo>

    /**
     * Finds a specific video by its content URI.
     *
     * @param uri The content URI of the video.
     * @return The video if found, null otherwise.
     */
    suspend fun findVideo(uri: Uri): MediaVideo?

    /**
     * Finds a specific folder by its path.
     *
     * @param path The absolute path to the folder.
     * @return The folder if it exists and contains videos, null otherwise.
     */
    suspend fun findFolder(path: String): MediaFolder?
}
