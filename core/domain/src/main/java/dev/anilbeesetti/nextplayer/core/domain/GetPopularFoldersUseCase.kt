package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.FolderFilter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class GetPopularFoldersUseCase @Inject constructor(
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(limit: Int = 5): Flow<List<Folder>> {
        return combine(
            getSortedFoldersUseCase(FolderFilter.All),
            getSortedVideosUseCase(FolderFilter.All)
        ) { folders, videos ->
            folders.sortedWith(
                compareByDescending<Folder> { folder ->
                    videos.count { it.parentPath == folder.path && it.lastPlayedAt != null }
                }.thenByDescending { folder ->
                    videos.filter { it.parentPath == folder.path }.maxOfOrNull { it.lastPlayedAt?.time ?: 0L } ?: 0L
                }.thenByDescending { folder ->
                    folder.videosCount
                },
            ).take(limit)
        }.flowOn(defaultDispatcher)
    }
}
