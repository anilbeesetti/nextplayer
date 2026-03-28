package dev.anilbeesetti.nextplayer.core.domain

import android.os.Environment
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.FolderFilter
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn

class GetSortedMediaUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(folderPath: String? = null): Flow<MediaHolder?> {
        return preferencesRepository.applicationPreferences.flatMapLatest { preferences ->
            val filter = getFolderFilter(folderPath, preferences.mediaViewMode)

            val videosFlow = getSortedVideosUseCase(filter)
            val foldersFlow = getSortedFoldersUseCase(filter)

            combine(videosFlow, foldersFlow) { videos, folders ->
                createMediaHolder(folderPath, preferences.mediaViewMode, videos, folders)
            }
        }.flowOn(defaultDispatcher)
    }
    private fun getFolderFilter(folderPath: String?, mediaViewMode: MediaViewMode): FolderFilter {
        return if (folderPath != null) {
            FolderFilter.WithPath(folderPath)
        } else {
            when (mediaViewMode) {
                MediaViewMode.FOLDER_TREE -> FolderFilter.WithPath(folderPath = Environment.getExternalStorageDirectory().path)
                MediaViewMode.FOLDERS -> FolderFilter.All
                MediaViewMode.VIDEOS -> FolderFilter.All
            }
        }
    }

    private fun createMediaHolder(
        folderPath: String?,
        mediaViewMode: MediaViewMode,
        videos: List<Video>,
        folders: List<Folder>,
    ): MediaHolder {
        return when (mediaViewMode) {
            MediaViewMode.FOLDER_TREE -> MediaHolder(
                videos = videos,
                folders = folders,
            )
            MediaViewMode.FOLDERS -> if (folderPath == null) {
                MediaHolder(
                    videos = emptyList(),
                    folders = folders,
                )
            } else {
                MediaHolder(
                    videos = videos,
                    folders = emptyList(),
                )
            }
            MediaViewMode.VIDEOS -> MediaHolder(
                videos = videos,
                folders = emptyList(),
            )
        }
    }
}

data class MediaHolder(
    val videos: List<Video>,
    val folders: List<Folder>,
)
