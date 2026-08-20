package com.graviton.core.domain

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import com.graviton.core.common.Dispatcher
import com.graviton.core.common.NextDispatchers
import com.graviton.core.common.extensions.getPath
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.model.MediaViewMode
import com.graviton.core.model.Video
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class GetSortedPlaylistUseCase @Inject constructor(
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
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
