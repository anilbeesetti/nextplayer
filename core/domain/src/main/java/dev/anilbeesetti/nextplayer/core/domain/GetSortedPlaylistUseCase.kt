package dev.anilbeesetti.nextplayer.core.domain

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class GetSortedPlaylistUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke(uri: Uri): List<Uri> = withContext(Dispatchers.Default) {
        val path = context.getPath(uri) ?: return@withContext emptyList()
        val parent = File(path).parent

        val videos = getSortedVideosUseCase.invoke().first()
        val preferences = preferencesRepository.appPrefsFlow.first()
        videos.filter {
            if (preferences.groupVideosByFolder) {
                parent == null || File(it.path).parent == parent
            } else {
                true
            }
        }.map { Uri.parse(it.uriString) }
    }
}
