package dev.anilbeesetti.nextplayer.core.domain

import android.os.Environment
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Use case for retrieving sorted media (videos and folders) based on the current view mode.
 *
 * This is the main entry point for the media picker UI. It delegates to specialized use cases
 * based on the user's selected [MediaViewMode]:
 * - [MediaViewMode.FOLDER_TREE]: Uses [GetFolderTreeMediaUseCase] for hierarchical folder view
 * - [MediaViewMode.FOLDERS]: Shows flat list of folders, or videos when inside a folder
 * - [MediaViewMode.VIDEOS]: Shows flat list of all videos
 */
class GetSortedMediaUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    private val getFolderTreeMediaUseCase: GetFolderTreeMediaUseCase,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    /**
     * Retrieves sorted media based on the current view mode and folder path.
     *
     * @param folderPath The folder path to display. When null, shows root-level content
     *                   based on the view mode.
     * @return A flow emitting [MediaHolder] with videos and folders appropriate for the view mode.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(folderPath: String? = null): Flow<MediaHolder?> {
        val rootPath = folderPath ?: Environment.getExternalStorageDirectory().path
        return preferencesRepository.applicationPreferences.flatMapLatest { preferences ->
            when (preferences.mediaViewMode) {
                MediaViewMode.FOLDER_TREE -> {
                    getFolderTreeMediaUseCase(rootPath)
                }

                MediaViewMode.FOLDERS -> {
                    if (folderPath == null) {
                        // At root: show all folders as a flat list
                        getSortedFoldersUseCase().map { folders ->
                            MediaHolder(videos = emptyList(), folders = folders)
                        }
                    } else {
                        getSortedVideosUseCase(folderPath).map { videos ->
                            val directVideos = videos.filter { it.parentPath == folderPath }
                            MediaHolder(videos = directVideos, folders = emptyList())
                        }
                    }
                }

                MediaViewMode.VIDEOS -> {
                    getSortedVideosUseCase(rootPath).map { videos ->
                        MediaHolder(videos = videos, folders = emptyList())
                    }
                }
            }
        }.flowOn(defaultDispatcher)
    }
}

/**
 * Container for media items (videos and folders) to be displayed in the UI.
 *
 * @property videos List of video items to display.
 * @property folders List of folder items to display.
 */
data class MediaHolder(
    val videos: List<Video>,
    val folders: List<Folder>,
)
