package dev.anilbeesetti.nextplayer.core.domain

import android.os.Environment
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.recentPlayed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for retrieving the most recently played video.
 *
 * Returns the video with the most recent play timestamp, optionally
 * filtered to a specific folder path based on the current view mode.
 */
class GetRecentlyPlayedVideoUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(folderPath: String? = null): Flow<Video?> {
        return preferencesRepository.applicationPreferences.flatMapLatest { preferences ->
            val searchPath = folderPath ?: Environment.getExternalStorageDirectory().path

            getSortedVideosUseCase(searchPath).map { videos ->
                // Filter based on view mode when folderPath is provided
                val filteredVideos = if (folderPath != null) {
                    when (preferences.mediaViewMode) {
                        MediaViewMode.FOLDER_TREE -> videos // All descendants
                        MediaViewMode.FOLDERS -> videos.filter { it.parentPath == folderPath }
                        MediaViewMode.VIDEOS -> videos
                    }
                } else {
                    videos
                }
                filteredVideos.recentPlayed()
            }
        }.flowOn(defaultDispatcher)
    }
}
