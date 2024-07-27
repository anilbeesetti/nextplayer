package dev.anilbeesetti.nextplayer.core.domain

import android.os.Environment
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Sort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

class GetSortedFolderTreeUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(folderPath: String? = null): Flow<Folder> {
        return combine(
            mediaRepository.getVideosFlow(),
            preferencesRepository.applicationPreferences,
        ) { videos, preferences ->
            val folder = folderPath?.let { File(it) } ?: Environment.getExternalStorageDirectory()

            val nonExcludedVideos = videos.filterNot {
                it.parentPath in preferences.excludeFolders
            }
            
            val folders = folder.listFiles { file ->
                file.isDirectory
            }?.filter { directory ->
                val videosCount = nonExcludedVideos.count { video -> video.path.startsWith(directory.path) }
                videosCount > 0
            }?.map { directory ->
                Folder(
                    name = directory.name,
                    path = directory.path,
                    dateModified = directory.lastModified(),
                    mediaList = nonExcludedVideos.filter { it.parentPath == directory.path },
                    folderList = invoke(directory.path).first().folderList,
                )
            } ?: emptyList()
            
            val folderVideos = nonExcludedVideos.filter { it.parentPath == folder.path }
            
            val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)
            
            return@combine Folder(
                name = folder.name,
                path = folder.path,
                dateModified = folder.lastModified(),
                mediaList = folderVideos.sortedWith(sort.videoComparator()),
                folderList = folders.sortedWith(sort.folderComparator())
            )
        }.flowOn(defaultDispatcher)
    }
}