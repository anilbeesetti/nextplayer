package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Video
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class GetAllFoldersUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher
) {

    operator fun invoke(): Flow<List<Folder>> {
        return combine(
            videoRepository.getVideosFlow(),
            preferencesRepository.applicationPreferences
        ) { videoItems, preferences ->
            videoItems
                .toFolders(preferences.excludeFolders)
                .sortedBy { it.path }
        }.flowOn(defaultDispatcher)
    }
}

fun List<Video>.toFolders(excludedFolders: List<String>? = null) =
    groupBy { File(it.path).parentFile!! }
        .map { (file, videos) ->
            Folder(
                path = file.path,
                name = file.prettyName,
                mediaCount = videos.size,
                mediaSize = videos.sumOf { it.size },
                isExcluded = if (excludedFolders == null) false else file.path in excludedFolders
            )
        }
