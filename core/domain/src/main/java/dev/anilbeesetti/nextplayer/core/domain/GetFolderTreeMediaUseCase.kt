package dev.anilbeesetti.nextplayer.core.domain

import android.os.Environment
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

class GetFolderTreeMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(folderPath: String = Environment.getExternalStorageDirectory().path): Flow<MediaHolder> {
        return combine(
            mediaRepository.observeVideos(folderPath),
            preferencesRepository.applicationPreferences,
        ) { videos, preferences ->
            val nonExcludedVideos = videos.filterNot { it.parentPath in preferences.excludeFolders }

            val directVideos = nonExcludedVideos.filter { it.parentPath == folderPath }
            val nestedVideos = nonExcludedVideos.filter { it.parentPath != folderPath }

            val videosByDisplayFolder = nestedVideos.groupBy { video ->
                findDisplayFolderPath(video.path, folderPath)
            }.filterKeys { it != null }

            val displayFolders = buildDisplayFolders(
                rootPath = folderPath,
                videosByDisplayFolder = videosByDisplayFolder,
                allNestedVideos = nestedVideos,
                excludedFolders = preferences.excludeFolders,
            )

            val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)
            MediaHolder(
                videos = directVideos.sortedWith(sort.videoComparator()),
                folders = displayFolders.sortedWith(sort.folderComparator()),
            )
        }.flowOn(defaultDispatcher)
    }

    /**
     * Finds the display folder path for a video.
     *
     * The display folder is the folder that is a direct child of the root path.
     * For example, if rootPath is "/storage/0" and video is at "/storage/0/Movies/Action/film.mp4",
     * the display folder path is "/storage/0/Movies".
     *
     * @param videoPath The full path to the video file.
     * @param rootPath The current root folder path being viewed.
     * @return The display folder path, or null if the video is directly in rootPath.
     */
    private fun findDisplayFolderPath(videoPath: String, rootPath: String): String? {
        val videoFile = File(videoPath)
        return videoFile.parentFile?.walkUp()?.firstOrNull { it.parent == rootPath }?.path
    }

    /**
     * Walks up the directory tree from this file to the root.
     */
    private fun File.walkUp(): Sequence<File> = generateSequence(this) { it.parentFile }

    /**
     * Builds the list of display folders with computed statistics.
     *
     * @param rootPath The current root folder path.
     * @param videosByDisplayFolder Videos grouped by their display folder path.
     * @param allNestedVideos All videos that are in subfolders (not direct children of rootPath).
     * @param excludedFolders Collection of folder paths to exclude.
     * @return List of [Folder] objects representing the display folders.
     */
    private fun buildDisplayFolders(
        rootPath: String,
        videosByDisplayFolder: Map<String?, List<Video>>,
        allNestedVideos: List<Video>,
        excludedFolders: Collection<String>,
    ): List<Folder> {
        return videosByDisplayFolder.mapNotNull { (displayFolderPath, videosInDisplayFolder) ->
            if (displayFolderPath == null || displayFolderPath in excludedFolders) return@mapNotNull null

            val displayFolder = File(displayFolderPath)

            // Get all videos that are descendants of this display folder
            val allDescendantVideos = allNestedVideos.filter { video ->
                video.path.startsWith(displayFolderPath + File.separator)
            }

            // Count direct videos (videos directly in the display folder)
            val directVideosCount = allDescendantVideos.count { it.parentPath == displayFolderPath }

            // Count direct subfolders that contain videos
            val directSubfoldersCount = countDirectSubfolders(displayFolderPath, allDescendantVideos)

            Folder(
                name = displayFolder.name,
                path = displayFolderPath,
                parentPath = rootPath,
                dateModified = allDescendantVideos.maxOfOrNull { it.dateModified } ?: 0L,
                totalSize = allDescendantVideos.sumOf { it.size },
                totalDuration = allDescendantVideos.sumOf { it.duration },
                videosCount = directVideosCount,
                foldersCount = directSubfoldersCount,
            )
        }
    }

    /**
     * Counts the number of direct subfolders that contain videos.
     *
     * @param folderPath The folder to count subfolders for.
     * @param descendantVideos All videos that are descendants of this folder.
     * @return The count of direct subfolders containing videos.
     */
    private fun countDirectSubfolders(folderPath: String, descendantVideos: List<Video>): Int {
        val folderPrefix = folderPath + File.separator
        return descendantVideos
            .filter { it.parentPath != folderPath } // Exclude direct videos
            .map { video ->
                // Extract the direct subfolder name
                val relativePath = video.parentPath.removePrefix(folderPrefix)
                relativePath.substringBefore(File.separator)
            }
            .distinct()
            .count()
    }
}
