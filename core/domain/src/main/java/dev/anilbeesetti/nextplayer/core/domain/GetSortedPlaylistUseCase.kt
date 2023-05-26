package dev.anilbeesetti.nextplayer.core.domain

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

class GetSortedPlaylistUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository
) {

    suspend operator fun invoke(path: String? = null): List<Uri> {
        val videos = getSortedVideosUseCase.invoke(path).first()
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