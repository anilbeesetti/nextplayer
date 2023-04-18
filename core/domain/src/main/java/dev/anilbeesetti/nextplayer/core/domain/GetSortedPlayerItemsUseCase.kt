package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.model.PlayerItem
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File

class GetSortedPlayerItemsUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository
) {

    /**
     * Returns a [Flow] of [List] of [PlayerItem]s.
     *
     * @param path The path of the folder to get the videos from.
     * If null, all videos will be returned.
     * If not null, only videos from the folder will be returned,
     * if [dev.anilbeesetti.nextplayer.core.datastore.AppPreferences.groupVideosByFolder] is true.
     */
    operator fun invoke(path: String? = null): Flow<List<PlayerItem>> {
        return combine(
            preferencesRepository.appPreferencesFlow,
            getSortedVideosUseCase.invoke()
        ) { preferences, videos ->
            videos.filter {
                if (preferences.groupVideosByFolder) {
                    path == null || File(it.path).parent == path
                } else {
                    true
                }
            }.map {
                PlayerItem(
                    path = it.path,
                    uriString = it.uriString,
                    duration = it.duration
                )
            }
        }
    }
}
