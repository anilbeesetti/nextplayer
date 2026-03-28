package dev.anilbeesetti.nextplayer.core.domain

import android.os.Environment
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.FolderFilter
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import java.io.File
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
            val folderPath = folderPath.takeIf { it != null } ?: when (preferences.mediaViewMode) {
                MediaViewMode.FOLDER_TREE -> Environment.getExternalStorageDirectory()?.path
                MediaViewMode.FOLDERS -> null
                MediaViewMode.VIDEOS -> null
            }

            val filter = if (folderPath != null) {
                FolderFilter.WithPath(folderPath)
            } else {
                when (preferences.mediaViewMode) {
                    MediaViewMode.FOLDER_TREE -> FolderFilter.WithPath(folderPath = Environment.getExternalStorageDirectory().path)
                    MediaViewMode.FOLDERS -> FolderFilter.All
                    MediaViewMode.VIDEOS -> FolderFilter.All
                }
            }

            val videos = getSortedVideosUseCase(filter)
            val folders = getSortedFoldersUseCase(filter)

            combine(videos, folders) { videos, folders ->
                when (preferences.mediaViewMode) {
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
        }.flowOn(defaultDispatcher)
    }
}

data class MediaHolder(
    val videos: List<Video>,
    val folders: List<Folder>,
)
