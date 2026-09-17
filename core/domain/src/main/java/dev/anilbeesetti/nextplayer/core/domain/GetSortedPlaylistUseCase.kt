package dev.anilbeesetti.nextplayer.core.domain

import android.content.Context
import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.di.DiQualifiers
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Named

@Factory
class GetSortedPlaylistUseCase(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val context: Context,
    @Named(DiQualifiers.DEFAULT_DISPATCHER) private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(uri: Uri): List<Video> = withContext(defaultDispatcher) {
        val path = context.getPath(uri) ?: return@withContext emptyList()
        val parent = File(path).parent ?: return@withContext emptyList()
        val preferences = preferencesRepository.applicationPreferences.first()

        // The playlist must match the order of the list the video was launched from.
        when (preferences.mediaViewMode) {
            MediaViewMode.FOLDER_TREE -> {
                // Tree mode: the folder and its subfolders.
                getSortedVideosUseCase(parent).first()
            }
            MediaViewMode.FOLDERS -> {
                // Folders mode: only videos directly in the same folder.
                getSortedVideosUseCase(parent).first().filter { it.parentPath == parent }
            }
            MediaViewMode.VIDEOS -> {
                // Videos mode shows a single global list across all storage; play in that order.
                getSortedVideosUseCase().first()
            }
        }
    }
}
