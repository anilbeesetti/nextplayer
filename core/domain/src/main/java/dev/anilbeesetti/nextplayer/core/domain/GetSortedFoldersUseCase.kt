package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.SortBy
import dev.anilbeesetti.nextplayer.core.model.SortOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetSortedFoldersUseCase @Inject constructor(
    private val getAllFoldersUseCase: GetAllFoldersUseCase,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher
) {

    operator fun invoke(): Flow<List<Folder>> {
        return combine(
            getAllFoldersUseCase.invoke(),
            preferencesRepository.appPrefsFlow
        ) { allFolders, preferences ->

            val folders = allFolders.filter { !it.isExcluded }

            when (preferences.sortOrder) {
                SortOrder.ASCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> folders.sortedBy { it.name.lowercase() }
                        SortBy.LENGTH -> folders.sortedBy { it.mediaCount }
                        SortBy.PATH -> folders.sortedBy { it.path.lowercase() }
                        SortBy.SIZE -> folders.sortedBy { it.mediaSize }
                    }
                }

                SortOrder.DESCENDING -> {
                    when (preferences.sortBy) {
                        SortBy.TITLE -> folders.sortedByDescending { it.name.lowercase() }
                        SortBy.LENGTH -> folders.sortedByDescending { it.mediaCount }
                        SortBy.PATH -> folders.sortedByDescending { it.path.lowercase() }
                        SortBy.SIZE -> folders.sortedByDescending { it.mediaSize }
                    }
                }
            }
        }.flowOn(defaultDispatcher)
    }
}
