package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

data class SearchResults(
    val folders: List<Folder> = emptyList(),
    val videos: List<Video> = emptyList(),
) {
    val isEmpty: Boolean
        get() = folders.isEmpty() && videos.isEmpty()

    val totalCount: Int
        get() = folders.size + videos.size
}

fun SearchResults.asRootFolder(): Folder {
    return Folder.rootFolder.copy(
        mediaList = videos,
        folderList = folders,
    )
}

class SearchMediaUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val getSortedFoldersUseCase: GetSortedFoldersUseCase,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(query: String): Flow<SearchResults> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return flowOf(SearchResults())
        }

        return combine(
            getSortedVideosUseCase(),
            getSortedFoldersUseCase(),
        ) { videos, folders ->
            val queryLower = normalizedQuery.lowercase()

            val matchingFolders = folders.filter { folder ->
                folder.name.lowercase().contains(queryLower) ||
                    folder.path.lowercase().contains(queryLower)
            }

            val matchingVideos = videos.filter { video ->
                video.displayName.lowercase().contains(queryLower) ||
                    video.nameWithExtension.lowercase().contains(queryLower) ||
                    video.path.lowercase().contains(queryLower)
            }

            SearchResults(
                folders = matchingFolders,
                videos = matchingVideos,
            )
        }.flowOn(defaultDispatcher)
    }
}
