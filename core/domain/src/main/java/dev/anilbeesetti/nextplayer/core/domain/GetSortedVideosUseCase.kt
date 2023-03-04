package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.models.Video
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetSortedVideosUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository
) {

    operator fun invoke(): Flow<List<Video>> {
        return combine(
            videoRepository.getVideosFlow(),
            preferencesRepository.preferences
        ) { videoItems, preferences ->
            when (preferences.sortOrder) {
                SortOrder.ASCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> videoItems.sortedBy { it.displayName }
                        SortBy.DURATION -> videoItems.sortedBy { it.duration }
                    }
                }
                SortOrder.DESCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> videoItems.sortedByDescending { it.displayName }
                        SortBy.DURATION -> videoItems.sortedByDescending { it.duration }
                    }
                }
            }
        }
    }
}
