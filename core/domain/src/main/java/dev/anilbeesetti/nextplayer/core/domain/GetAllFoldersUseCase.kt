package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VideoRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

class GetAllFoldersUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher
) {

    operator fun invoke(): Flow<List<Folder>> {
        return combine(
            videoRepository.getVideosFlow(),
            preferencesRepository.appPrefsFlow
        ) { videoItems, preferences ->

            val folders = videoItems.groupBy {
                File(it.path).parentFile!!
            }.map { (file, videos) ->
                Folder(
                    path = file.path,
                    name = file.prettyName,
                    mediaCount = videos.size,
                    mediaSize = videos.sumOf { it.size },
                    isExcluded = file.path in preferences.excludeFolders
                )
            }
            folders.sortedBy { it.path }
        }.flowOn(defaultDispatcher)
    }
}
