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
            mediaRepository.getVideosFlowFromDirectory(folderPath)
        } else {
            mediaRepository.getVideosFlow()
        }

        return combine(
            videosFlow,
            preferencesRepository.applicationPreferences
        ) { videoItems, preferences ->

            val filteredVideos = videoItems.filterNot {
                it.parentPath in preferences.excludeFolders
            }

            when (preferences.sortOrder) {
                SortOrder.ASCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> filteredVideos.sortedBy { it.displayName.lowercase() }
                        SortBy.LENGTH -> filteredVideos.sortedBy { it.duration }
                        SortBy.PATH -> filteredVideos.sortedBy { it.path.lowercase() }
                        SortBy.SIZE -> filteredVideos.sortedBy { it.size }
                        SortBy.DATE -> filteredVideos.sortedBy { it.dateModified }
                    }
                }

                SortOrder.DESCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> filteredVideos.sortedByDescending { it.displayName.lowercase() }
                        SortBy.LENGTH -> filteredVideos.sortedByDescending { it.duration }
                        SortBy.PATH -> filteredVideos.sortedByDescending { it.path.lowercase() }
                        SortBy.SIZE -> filteredVideos.sortedByDescending { it.size }
                        SortBy.DATE -> filteredVideos.sortedByDescending { it.dateModified }
                    }
                }
            }
        }.flowOn(defaultDispatcher)
    }
}
