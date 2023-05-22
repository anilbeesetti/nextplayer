package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.data.models.Folder
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.datastore.SortBy
import dev.anilbeesetti.nextplayer.core.datastore.SortOrder
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class GetSortedFoldersUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository
) {

    operator fun invoke(): Flow<List<Folder>> {
        return combine(
            videoRepository.getVideosFlow(),
            preferencesRepository.appPreferencesFlow
        ) { videoItems, preferences ->

            val folders = videoItems.groupBy { File(it.path).parentFile!! }
                .map { (file, videos) ->
                    Folder(
                        path = file.path,
                        name = file.prettyName,
                        mediaCount = videos.size,
                        mediaSize = videos.sumOf { it.size }
                    )
                }

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
        }.flowOn(Dispatchers.Default)
    }
}
