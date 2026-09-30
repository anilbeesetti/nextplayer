package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.di.DiQualifiers
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.isNew
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

@Factory
class GetSortedFoldersUseCase(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Named(DiQualifiers.DEFAULT_DISPATCHER) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(folderPath: String? = null): Flow<List<Folder>> {
        return combine(
            mediaRepository.observeVideos(folderPath),
            preferencesRepository.applicationPreferences,
            newVideoClock(),
        ) { videos, preferences, nowMillis ->
            val folders = videos
                .groupBy { it.parentPath }
                .map { (path, folderVideos) ->
                    Folder(
                        name = File(path).prettyName,
                        path = path,
                        parentPath = File(path).parent,
                        dateModified = folderVideos.maxOfOrNull { it.dateModified } ?: 0L,
                        totalSize = folderVideos.sumOf { it.size },
                        totalDuration = folderVideos.sumOf { it.duration },
                        videosCount = folderVideos.size,
                        newVideosCount = folderVideos.count {
                            it.isNew(
                                nowMillis = nowMillis,
                                thresholdDays = preferences.newVideoThresholdDays,
                            )
                        },
                    )
                }
                .filterNot { it.path in preferences.excludeFolders }

            val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)
            folders.sortedWith(sort.folderComparator())
        }.flowOn(defaultDispatcher)
    }
}
