package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.Folder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class GetPopularFoldersUseCase @Inject constructor(
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(limit: Int = 5): Flow<List<Folder>> {
        return getSortedFoldersUseCase().map { folders ->
            folders.sortedWith(
                compareByDescending<Folder> { folder ->
                    folder.allMediaList.count { it.lastPlayedAt != null }
                }.thenByDescending { folder ->
                    folder.recentlyPlayedVideo?.lastPlayedAt?.time ?: 0L
                }.thenByDescending { folder ->
                    folder.mediaList.size
                },
            ).take(limit)
        }.flowOn(defaultDispatcher)
    }
}
