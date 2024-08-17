package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class GetSortedMediaUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    private val getSortedFolderTreeUseCase: GetSortedFolderTreeUseCase,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(folderPath: String? = null): Flow<Folder?> {
        return combine(
            getSortedVideosUseCase(folderPath),
            getSortedFoldersUseCase(),
            getSortedFolderTreeUseCase(folderPath),
            preferencesRepository.applicationPreferences,
        ) { videos, folders, folderTree, preferences ->
            when (preferences.mediaViewMode) {
                MediaViewMode.FOLDER_TREE -> folderTree
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
        }.flowOn(defaultDispatcher)
    }
}
