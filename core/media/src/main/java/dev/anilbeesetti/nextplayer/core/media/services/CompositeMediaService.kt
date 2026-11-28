package dev.anilbeesetti.nextplayer.core.media.services

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * The [MediaService] the app uses: MediaStore videos plus videos read directly from external
 * drives that MediaStore does not list (see [ExternalVolumeMediaService]).
 */
@Single
class CompositeMediaService(
    private val mediaStore: MediaStoreMediaService,
    private val externalVolumes: ExternalVolumeMediaService,
) : MediaService {

    override fun observeVideos(folderPath: String?): Flow<List<MediaVideo>> = combine(
        mediaStore.observeVideos(folderPath),
        externalVolumes.changes,
    ) { storeVideos, _ ->
        merge(
            storeVideos,
            externalVolumes.fetchVideos(folderPath),
        )
    }
        .flowOn(Dispatchers.IO)

    override fun observeFolders(folderPath: String?): Flow<List<MediaFolder>> =
        observeVideos(folderPath).map { it.toFolders() }

    override fun observeTrashVideos(): Flow<List<MediaVideo>> = mediaStore.observeTrashVideos()

    override suspend fun fetchVideos(folderPath: String?): List<MediaVideo> = merge(
        mediaStore.fetchVideos(folderPath),
        externalVolumes.fetchVideos(folderPath),
    )

    override suspend fun fetchFolders(folderPath: String?): List<MediaFolder> =
        withContext(Dispatchers.IO) { fetchVideos(folderPath).toFolders() }

    override suspend fun findVideo(uri: Uri): MediaVideo? =
        mediaStore.findVideo(uri) ?: externalVolumes.findVideo(uri)

    override suspend fun findFolder(path: String): MediaFolder? {
        mediaStore.findFolder(path)?.let { return it }
        val videos = externalVolumes.fetchVideos(path)
        if (videos.isEmpty()) return null
        val subfolders = videos
            .filter { it.parentPath != path }
            .map { it.parentPath.removePrefix("$path/").substringBefore('/') }
            .distinct()
        val folder = File(path)
        return MediaFolder(
            path = path,
            name = folder.prettyName,
            dateModified = videos.maxOf { it.dateModified },
            totalSize = videos.sumOf { it.size },
            totalDuration = videos.sumOf { it.duration },
            videosCount = videos.count { it.parentPath == path },
            foldersCount = subfolders.size,
        )
    }

    /** A video MediaStore already lists (same path) is not shown a second time. */
    private fun merge(
        storeVideos: List<MediaVideo>,
        folderVideos: List<MediaVideo>,
    ): List<MediaVideo> {
        if (folderVideos.isEmpty()) return storeVideos
        val known = storeVideos.mapTo(mutableSetOf()) { it.path }
        return storeVideos + folderVideos.filter { it.path !in known }
    }

    private fun List<MediaVideo>.toFolders(): List<MediaFolder> =
        groupBy { File(it.path).parentFile }.mapNotNull { (folder, videos) ->
            folder?.let {
                MediaFolder(
                    path = it.path,
                    name = it.prettyName,
                    dateModified = videos.maxOf { video -> video.dateModified },
                    totalSize = videos.sumOf { video -> video.size },
                    totalDuration = videos.sumOf { video -> video.duration },
                    videosCount = videos.size,
                    foldersCount = 0,
                )
            }
        }
}
