package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import dev.anilbeesetti.nextplayer.core.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetSortedVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository
) {

    operator fun invoke(folderPath: String? = null): Flow<List<Video>> {
        return combine(
            videoRepository.getVideosFlow(),
            preferencesRepository.appPreferencesFlow
        ) { videoItems, preferences ->

            val filteredVideos = videoItems.filter {
                folderPath == null || it.path.substringBeforeLast("/") == folderPath
            }

            when (preferences.sortOrder) {
                SortOrder.ASCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> filteredVideos.sortedBy { it.displayName.lowercase() }
                        SortBy.LENGTH -> filteredVideos.sortedBy { it.duration }
                        SortBy.PATH -> filteredVideos.sortedBy { it.path.lowercase() }
                        SortBy.SIZE -> filteredVideos.sortedBy { it.size }
                    }
                }
                SortOrder.DESCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> filteredVideos.sortedByDescending { it.displayName.lowercase() }
                        SortBy.LENGTH -> filteredVideos.sortedByDescending { it.duration }
                        SortBy.PATH -> filteredVideos.sortedByDescending { it.path.lowercase() }
                        SortBy.SIZE -> filteredVideos.sortedByDescending { it.size }
                    }
                }
            }
        }.flowOn(Dispatchers.Default)
    }
}
