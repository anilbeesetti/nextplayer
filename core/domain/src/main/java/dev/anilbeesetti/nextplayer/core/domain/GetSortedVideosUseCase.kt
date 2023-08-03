package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class GetSortedVideosUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    operator fun invoke(folderPath: String? = null): Flow<List<Video>> {
        val videosFlow = if (folderPath != null) {
            mediaRepository.getVideosFlowFromFolderPath(folderPath)
        } else {
            mediaRepository.getVideosFlow()
        }

        return combine(
            videosFlow,
            preferencesRepository.applicationPreferences
        ) { videoItems, preferences ->

            val nonExcludedVideos = videoItems.filterNot {
                it.parentPath in preferences.excludeFolders
            }

            when (preferences.sortOrder) {
                SortOrder.ASCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> nonExcludedVideos.sortedBy { it.displayName.lowercase() }
                        SortBy.LENGTH -> nonExcludedVideos.sortedBy { it.duration }
                        SortBy.PATH -> nonExcludedVideos.sortedBy { it.path.lowercase() }
                        SortBy.SIZE -> nonExcludedVideos.sortedBy { it.size }
                        SortBy.DATE -> nonExcludedVideos.sortedBy { it.dateModified }
                    }
                }

                SortOrder.DESCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> nonExcludedVideos.sortedByDescending { it.displayName.lowercase() }
                        SortBy.LENGTH -> nonExcludedVideos.sortedByDescending { it.duration }
                        SortBy.PATH -> nonExcludedVideos.sortedByDescending { it.path.lowercase() }
                        SortBy.SIZE -> nonExcludedVideos.sortedByDescending { it.size }
                        SortBy.DATE -> nonExcludedVideos.sortedByDescending { it.dateModified }
                    }
                }
            }
        }.flowOn(defaultDispatcher)
    }
}
