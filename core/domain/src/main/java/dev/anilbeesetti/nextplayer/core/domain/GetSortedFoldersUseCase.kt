package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.models.Folder
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetSortedFoldersUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository
) {

    operator fun invoke(): Flow<List<Folder>> {
        return combine(
            videoRepository.getFoldersFlow(),
            preferencesRepository.appPreferencesFlow
        ) { videoItems, preferences ->
            when (preferences.sortOrder) {
                SortOrder.ASCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> videoItems.sortedBy { it.name.lowercase() }
                        SortBy.LENGTH -> videoItems.sortedBy { it.mediaCount }
                        SortBy.PATH -> videoItems.sortedBy { it.path.lowercase() }
                        SortBy.SIZE -> videoItems.sortedBy { it.mediaSize }
                    }
                }
                SortOrder.DESCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> videoItems.sortedByDescending { it.name.lowercase() }
                        SortBy.LENGTH -> videoItems.sortedByDescending { it.mediaCount }
                        SortBy.PATH -> videoItems.sortedByDescending { it.path.lowercase() }
                        SortBy.SIZE -> videoItems.sortedByDescending { it.mediaSize }
                    }
                }
            }
        }
    }
}
