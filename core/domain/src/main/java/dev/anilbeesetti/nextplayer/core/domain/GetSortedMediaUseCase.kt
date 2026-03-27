package dev.anilbeesetti.nextplayer.core.domain

import android.os.Environment
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
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
    operator fun invoke(folderPath: String? = null): Flow<Folder?> {
        return preferencesRepository.applicationPreferences.flatMapLatest { preferences ->
            val folderPath = folderPath.takeIf { it != null } ?: when (preferences.mediaViewMode) {
                MediaViewMode.FOLDER_TREE -> Environment.getExternalStorageDirectory()?.path
                MediaViewMode.FOLDERS -> null
                MediaViewMode.VIDEOS -> null
            }

            val videos = getSortedVideosUseCase(folderPath)
            val folders = getSortedFoldersUseCase(folderPath)

            combine(videos, folders) { videos, folders ->
                when (preferences.mediaViewMode) {
                    MediaViewMode.FOLDER_TREE -> Folder.rootFolder.copy(
                        mediaList = videos,
                        folderList = folders,
                    )
                    MediaViewMode.FOLDERS -> if (folderPath == null) {
                        Folder.rootFolder.copy(
                            mediaList = emptyList(),
                            folderList = folders,
                        )
                    } else {
                        val file = File(folderPath)
                        Folder(
                            name = file.name,
                            path = file.path,
                            dateModified = file.lastModified(),
                            mediaList = videos,
                            folderList = emptyList(),
                        )
                    }
                    MediaViewMode.VIDEOS -> Folder.rootFolder.copy(
                        mediaList = videos,
                        folderList = emptyList(),
                    )
                }
            }
        }.flowOn(defaultDispatcher)
    }
}
