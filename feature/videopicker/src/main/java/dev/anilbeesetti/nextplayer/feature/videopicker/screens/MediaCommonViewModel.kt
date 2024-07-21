package dev.anilbeesetti.nextplayer.feature.videopicker.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.services.SystemService
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.remotesubs.service.Subtitle
import dev.anilbeesetti.nextplayer.core.remotesubs.service.SubtitlesService
import dev.anilbeesetti.nextplayer.core.ui.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaCommonViewModel @Inject constructor(
    private val systemService: SystemService,
    private val subtitlesService: SubtitlesService,
    private val mediaSynchronizer: MediaSynchronizer,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(MediaCommonUiState())
    val uiState = uiStateInternal.asStateFlow()

    fun getSubtitlesOnline(video: Video) {
        uiStateInternal.update {
            it.copy(
                dialog = MediaCommonDialog.GetSubtitlesOnline(
                    video = video,
                    onDismiss = { dismissDialog() },
                    onConfirm = { searchText, language ->
                        uiStateInternal.update { currentState ->
                            currentState.copy(
                                dialog = MediaCommonDialog.Loading(
                                    message = systemService.getString(R.string.searching_subtitles),
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

    fun onRefreshClicked() {
        viewModelScope.launch {
            uiStateInternal.update { it.copy(isRefreshing = true) }
            mediaSynchronizer.refresh()
            uiStateInternal.update { it.copy(isRefreshing = false) }
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
                        dialog = MediaCommonDialog.SubtitleResults(
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
                        dialog = MediaCommonDialog.Error(
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

data class MediaCommonUiState(
    val isRefreshing: Boolean = false,
    val dialog: MediaCommonDialog? = null,
)


sealed interface MediaCommonDialog {
    data class Loading(val message: String?) : MediaCommonDialog
    data class Error(val message: String?, val onDismiss: () -> Unit) : MediaCommonDialog
    data class GetSubtitlesOnline(
        val video: Video,
        val onDismiss: () -> Unit,
        val onConfirm: (searchText: String?, language: String) -> Unit,
    ) : MediaCommonDialog

    data class SubtitleResults(
        val results: List<Subtitle>,
        val onDismiss: () -> Unit,
        val onSubtitleSelected: (Subtitle) -> Unit,
    ) : MediaCommonDialog
}