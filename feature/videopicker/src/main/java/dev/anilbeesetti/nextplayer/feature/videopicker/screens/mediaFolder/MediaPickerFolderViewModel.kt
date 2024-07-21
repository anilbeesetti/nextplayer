package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediaFolder

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.common.services.SystemService
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.sync.MediaInfoSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.remotesubs.service.Subtitle
import dev.anilbeesetti.nextplayer.core.remotesubs.service.SubtitlesService
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.FolderArgs
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.VideosState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaPickerFolderViewModel @Inject constructor(
    getSortedVideosUseCase: GetSortedVideosUseCase,
    savedStateHandle: SavedStateHandle,
    private val mediaService: MediaService,
    private val preferencesRepository: PreferencesRepository,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
    private val subtitlesService: SubtitlesService,
    private val systemService: SystemService,
) : ViewModel() {

    private val folderArgs = FolderArgs(savedStateHandle)

    val folderPath = folderArgs.folderId
    val folderName = File(folderPath).prettyName

    private val uiStateInternal = MutableStateFlow(MediaPicketFolderUiState())
    val uiState = uiStateInternal.asStateFlow()

    val videos = getSortedVideosUseCase.invoke(folderPath)
        .map { VideosState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VideosState.Loading,
        )

    val preferences = preferencesRepository.applicationPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ApplicationPreferences(),
        )

    fun deleteVideos(uris: List<String>) {
        viewModelScope.launch {
            mediaService.deleteMedia(uris.map { Uri.parse(it) })
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

    fun getSubtitlesOnline(video: Video) {
        uiStateInternal.update {
            it.copy(
                dialog = MediaPickerFolderScreenDialog.GetSubtitlesOnlineDialog(
                    video = video,
                    onDismiss = { dismissDialog() },
                    onConfirm = { searchText, language ->
                        uiStateInternal.update { currentState ->
                            currentState.copy(
                                dialog = MediaPickerFolderScreenDialog.LoadingDialog(
                                    messageRes = R.string.searching_subtitles,
                                ),
                            )
                        }
                        getSubtitleResultsAndShowDialog(
                            video = video,
                            searchText = searchText,
                            language = language,
                        )
                    },
                ),
            )
        }
    }

    private fun getSubtitleResultsAndShowDialog(video: Video, searchText: String?, language: String) {
        viewModelScope.launch {
            subtitlesService.search(
                video = video,
                searchText = searchText,
                languages = listOf(language),
            ).onSuccess { response ->
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        dialog = MediaPickerFolderScreenDialog.SubtitleResultsDialog(
                            results = response,
                            onDismiss = { dismissDialog() },
                            onSubtitleSelected = {
                                downloadSubtitle(it, video)
                                dismissDialog()
                            },
                        ),
                    )
                }
            }.onFailure { error ->
                uiStateInternal.update {
                    it.copy(
                        dialog = MediaPickerFolderScreenDialog.ErrorDialog(
                            message = error.localizedMessage ?: error.cause?.localizedMessage,
                            onDismiss = { dismissDialog() },
                        ),
                    )
                }
            }
        }
    }

    private fun downloadSubtitle(subtitle: Subtitle, video: Video) {
        systemService.showToast(R.string.downloading_subtitle)
        viewModelScope.launch {
            subtitlesService.download(
                subtitle = subtitle,
                name = video.displayName,
            ).onSuccess {
                if (it.message != null) {
                    systemService.showToast(it.message!!)
                } else {
                    systemService.showToast(R.string.subtitle_downloaded)
                }
            }.onFailure {
                if (it.message != null) {
                    systemService.showToast(it.message!!)
                } else {
                    systemService.showToast(R.string.error_downloading_subtitle)
                }
            }
        }
    }

    private fun dismissDialog() {
        uiStateInternal.update {
            it.copy(dialog = null)
        }
    }
}

data class MediaPicketFolderUiState(
    val dialog: MediaPickerFolderScreenDialog? = null,
)

sealed interface MediaPickerFolderScreenDialog {
    data class LoadingDialog(val messageRes: Int?) : MediaPickerFolderScreenDialog
    data class ErrorDialog(val message: String?, val onDismiss: () -> Unit) : MediaPickerFolderScreenDialog
    data class GetSubtitlesOnlineDialog(
        val video: Video,
        val onDismiss: () -> Unit,
        val onConfirm: (searchText: String?, language: String) -> Unit,
    ) : MediaPickerFolderScreenDialog

    data class SubtitleResultsDialog(
        val results: List<Subtitle>,
        val onDismiss: () -> Unit,
        val onSubtitleSelected: (Subtitle) -> Unit,
    ) : MediaPickerFolderScreenDialog
}
