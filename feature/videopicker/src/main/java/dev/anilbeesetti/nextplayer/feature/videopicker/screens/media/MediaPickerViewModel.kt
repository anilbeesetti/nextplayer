package dev.anilbeesetti.nextplayer.feature.videopicker.screens.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedFoldersUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.sync.MediaInfoSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    getSortedVideosUseCase: GetSortedVideosUseCase,
    getSortedFoldersUseCase: GetSortedFoldersUseCase,
    private val mediaService: MediaService,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
) : ViewModel() {

    val videosState = getSortedVideosUseCase.invoke()
        .map { VideosState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VideosState.Loading,
        )

    val foldersState = getSortedFoldersUseCase.invoke()
        .map { FoldersState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FoldersState.Loading,
        )

    val preferences = preferencesRepository.applicationPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ApplicationPreferences(),
        )

    fun updateMenu(applicationPreferences: ApplicationPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { applicationPreferences }
        }
    }

    fun deleteVideos(uris: List<String>) {
        viewModelScope.launch {
            mediaService.deleteMedia(uris.map { Uri.parse(it) })
        }
    }

    fun deleteFolders(paths: List<String>) {
        viewModelScope.launch {
            val uris = paths.flatMap { path ->
                mediaRepository.getVideosFlowFromFolderPath(path).first().mapNotNull {
                    Uri.parse(it.uriString)
                }
            }
            mediaService.deleteMedia(uris)
        }
    }

    fun addToMediaInfoSynchronizer(uri: Uri) {
        viewModelScope.launch {
            mediaInfoSynchronizer.addMedia(uri)
        }
    }

    fun renameVideo(uri: Uri, to: String) {
        viewModelScope.launch {
            mediaService.renameMedia(uri, to)
        }
    }
}
