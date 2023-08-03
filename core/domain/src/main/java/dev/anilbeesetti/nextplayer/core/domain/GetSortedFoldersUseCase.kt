package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
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
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher
) {

    operator fun invoke(): Flow<List<Folder>> {
        return combine(
            mediaRepository.getFoldersFlow(),
            preferencesRepository.applicationPreferences
        ) { folders, preferences ->

            val nonExcludedDirectories = folders.filterNot {
                it.path in preferences.excludeFolders
            }

            when (preferences.sortOrder) {
                SortOrder.ASCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> nonExcludedDirectories.sortedBy { it.name.lowercase() }
                        SortBy.LENGTH -> nonExcludedDirectories.sortedBy { it.mediaCount }
                        SortBy.PATH -> nonExcludedDirectories.sortedBy { it.path.lowercase() }
                        SortBy.SIZE -> nonExcludedDirectories.sortedBy { it.mediaSize }
                        SortBy.DATE -> nonExcludedDirectories.sortedBy { it.dateModified }
                    }
                }

                SortOrder.DESCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> nonExcludedDirectories.sortedByDescending { it.name.lowercase() }
                        SortBy.LENGTH -> nonExcludedDirectories.sortedByDescending { it.mediaCount }
                        SortBy.PATH -> nonExcludedDirectories.sortedByDescending { it.path.lowercase() }
                        SortBy.SIZE -> nonExcludedDirectories.sortedByDescending { it.mediaSize }
                        SortBy.DATE -> nonExcludedDirectories.sortedByDescending { it.dateModified }
                    }
                }
            }
        }.flowOn(defaultDispatcher)
    }
}
