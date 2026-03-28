package dev.anilbeesetti.nextplayer.core.domain

import android.os.Environment
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.FolderFilter
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.recentPlayed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetRecentlyPlayedVideoUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(folderPath: String? = null): Flow<Video?> {
        return preferencesRepository.applicationPreferences.flatMapLatest { preferences ->
            val recentlyPlayedVideoFilter = if (folderPath == null) {
                FolderFilter.All
            } else {
                when (preferences.mediaViewMode) {
                    MediaViewMode.FOLDER_TREE -> FolderFilter.WithPath(folderPath, directChildrenOnly = false)
                    MediaViewMode.FOLDERS -> FolderFilter.WithPath(folderPath, directChildrenOnly = true)
                    MediaViewMode.VIDEOS -> FolderFilter.WithPath(folderPath)
                }
            }

            getSortedVideosUseCase(recentlyPlayedVideoFilter).map { it.recentPlayed() }
        }.flowOn(defaultDispatcher)
    }
}