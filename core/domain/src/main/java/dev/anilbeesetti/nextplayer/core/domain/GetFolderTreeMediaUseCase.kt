package dev.anilbeesetti.nextplayer.core.domain

import android.os.Environment
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
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

/**
 * Produces the hierarchical (tree) view of media for a folder.
 *
 * Each level shows the videos directly inside the current folder plus a [Folder] for every
 * immediate subfolder that contains videos. The top level (folderPath == null) spans all storage
 * volumes: when more than one volume holds videos each volume is shown as a folder ("Internal
 * Storage", a USB drive, …); with a single volume its contents are shown directly.
 */
class GetFolderTreeMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(folderPath: String? = null): Flow<MediaHolder> {
        return combine(
            mediaRepository.observeVideos(folderPath),
            preferencesRepository.applicationPreferences,
        ) { videos, preferences ->
            val included = videos.filterNot { it.parentPath in preferences.excludeFolders }
            val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)

            if (folderPath != null) {
                mediaUnder(folderPath, included, preferences.excludeFolders, sort)
            } else {
                topLevelMedia(included, preferences.excludeFolders, sort)
            }
        }.flowOn(defaultDispatcher)
    }

    /**
     * The top level: one folder per storage volume that contains videos, or — when only a single
     * volume has videos — that volume's contents shown directly (no volume wrapper).
     */
    private fun topLevelMedia(videos: List<Video>, excludedFolders: Collection<String>, sort: Sort): MediaHolder {
        val volumeRoots = videos.mapNotNull { volumeRootOf(it.path) }.distinct()
        if (volumeRoots.size <= 1) {
            val root = volumeRoots.firstOrNull() ?: Environment.getExternalStorageDirectory().path
            return mediaUnder(root, videos, excludedFolders, sort)
        }
        val folders = volumeRoots
            .filterNot { it in excludedFolders }
            .map { volumeRoot -> summarize(volumeRoot, videosUnder(volumeRoot, videos)) }
        return MediaHolder(videos = emptyList(), folders = folders.sortedWith(sort.folderComparator()))
    }

    /** The videos directly inside [root] plus a [Folder] for each immediate subfolder with videos. */
    private fun mediaUnder(root: String, videos: List<Video>, excludedFolders: Collection<String>, sort: Sort): MediaHolder {
        val descendants = videosUnder(root, videos)
        val directVideos = descendants.filter { it.parentPath == root }
        val folders = immediateChildFolders(root, descendants)
            .filterNot { it in excludedFolders }
            .map { childPath -> summarize(childPath, videosUnder(childPath, descendants)) }

        return MediaHolder(
            videos = directVideos.sortedWith(sort.videoComparator()),
            folders = folders.sortedWith(sort.folderComparator()),
        )
    }

    /** Builds a [Folder] aggregating the stats of every video beneath [path]. */
    private fun summarize(path: String, descendantVideos: List<Video>): Folder {
        val file = File(path)
        return Folder(
            name = file.prettyName,
            path = path,
            parentPath = file.parent,
            dateModified = descendantVideos.maxOfOrNull { it.dateModified } ?: 0L,
            totalSize = descendantVideos.sumOf { it.size },
            totalDuration = descendantVideos.sumOf { it.duration },
            videosCount = descendantVideos.count { it.parentPath == path },
            foldersCount = immediateChildFolders(path, descendantVideos).size,
        )
    }

    /** Distinct immediate subfolders of [path] that contain at least one of [videos] (which are all beneath [path]). */
    private fun immediateChildFolders(path: String, videos: List<Video>): List<String> {
        val prefix = path + File.separator
        return videos
            .filter { it.parentPath != path }
            .map { prefix + it.parentPath.removePrefix(prefix).substringBefore(File.separator) }
            .distinct()
    }

    /** All videos located somewhere beneath [path]. */
    private fun videosUnder(path: String, videos: List<Video>): List<Video> {
        val prefix = path + File.separator
        return videos.filter { it.path.startsWith(prefix) }
    }

    /**
     * Infers the storage-volume root that contains [path] from its canonical MediaStore path:
     * "/storage/emulated/<user>" for emulated (internal) storage, or "/storage/<volume>" for
     * removable volumes (SD card / USB OTG). Returns null for unrecognised layouts.
     */
    private fun volumeRootOf(path: String): String? {
        val parts = path.split('/').filter { it.isNotEmpty() }
        return when {
            parts.size >= 3 && parts[0] == "storage" && parts[1] == "emulated" -> "/storage/emulated/${parts[2]}"
            parts.size >= 2 && parts[0] == "storage" -> "/storage/${parts[1]}"
            else -> null
        }
    }
}
