package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.model.FolderFilter
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
     * Observes folders matching the given filter.
     * Emits a new list whenever the underlying media changes.
     */
    fun observeFolders(filter: FolderFilter): Flow<List<MediaFolder>>

    /**
     * Observes videos matching the given filter.
     * Emits a new list whenever the underlying media changes.
     */
    fun observeVideos(filter: FolderFilter): Flow<List<MediaVideo>>

    /**
     * Fetches folders matching the given filter (one-shot).
     */
    suspend fun fetchFolders(filter: FolderFilter): List<MediaFolder>

    /**
     * Fetches videos matching the given filter (one-shot).
     */
    suspend fun fetchVideos(filter: FolderFilter): List<MediaVideo>

    /**
     * Finds a specific video by its content URI.
     * @return The video if found, null otherwise.
     */
    suspend fun findVideo(uri: Uri): MediaVideo?

    /**
     * Finds a specific folder by its path.
     * @return The folder if it exists and contains videos, null otherwise.
     */
    suspend fun findFolder(path: String): MediaFolder?
}
