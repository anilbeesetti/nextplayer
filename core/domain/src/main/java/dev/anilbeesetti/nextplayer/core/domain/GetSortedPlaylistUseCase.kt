package dev.anilbeesetti.nextplayer.core.domain

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class GetSortedPlaylistUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(uri: Uri): List<Uri> = withContext(defaultDispatcher) {
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
