package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.Folder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * Use case for retrieving the most popular folders based on video play history.
 *
 * Folders are ranked by:
 * 1. Number of videos played in the folder
 * 2. Most recent play time of any video in the folder
 * 3. Total video count in the folder
 */
class GetPopularFoldersUseCase @Inject constructor(
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(limit: Int = 5): Flow<List<Folder>> {
        return combine(
            getSortedFoldersUseCase(),
            getSortedVideosUseCase(),
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
