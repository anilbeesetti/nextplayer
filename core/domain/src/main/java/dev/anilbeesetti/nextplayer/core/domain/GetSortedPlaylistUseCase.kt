package dev.anilbeesetti.nextplayer.core.domain

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last

class GetSortedPlaylistUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository
) {

    suspend operator fun invoke(path: String? = null): List<Uri> {
        val videos = getSortedVideosUseCase.invoke().first()
        val preferences = preferencesRepository.appPreferencesFlow.first()

        return videos.filter {
            if (preferences.groupVideosByFolder) {
                path == null || File(it.path).parent == path
            } else {
                true
            }
        }.map { it.uri }
    }
}
