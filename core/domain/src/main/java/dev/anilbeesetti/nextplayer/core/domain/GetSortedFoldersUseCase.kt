package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.LocalVideoRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class GetSortedFoldersUseCase @Inject constructor(
    private val videoRepository: LocalVideoRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher
) {

    operator fun invoke(): Flow<List<Folder>> {
        return combine(
            videoRepository.getDirectoriesFlow(),
            preferencesRepository.applicationPreferences
        ) { directories, preferences ->

            when (preferences.sortOrder) {
                SortOrder.ASCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> directories.sortedBy { it.name.lowercase() }
                        SortBy.LENGTH -> directories.sortedBy { it.mediaCount }
                        SortBy.PATH -> directories.sortedBy { it.path.lowercase() }
                        SortBy.SIZE -> directories.sortedBy { it.mediaSize }
                        SortBy.DATE -> directories.sortedBy { it.dateModified }
                    }
                }

                SortOrder.DESCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> directories.sortedByDescending { it.name.lowercase() }
                        SortBy.LENGTH -> directories.sortedByDescending { it.mediaCount }
                        SortBy.PATH -> directories.sortedByDescending { it.path.lowercase() }
                        SortBy.SIZE -> directories.sortedByDescending { it.mediaSize }
                        SortBy.DATE -> directories.sortedByDescending { it.dateModified }
                    }
                }
            }
        }.flowOn(defaultDispatcher)
    }
}
