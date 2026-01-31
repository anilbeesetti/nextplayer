package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.Folder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn

class GetPopularFoldersUseCase @Inject constructor(
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(limit: Int = 5): Flow<List<Folder>> {
        return getSortedFoldersUseCase()
            .map { folders ->
                folders
                    .sortedByDescending { it.mediaList.size }
                    .take(limit)
            }
            .flowOn(defaultDispatcher)
    }
}
