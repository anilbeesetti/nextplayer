package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.domain.ObservePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.ActionState
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    observePlaylist: ObservePlaylistUseCase,
    private val playlistRepository: PlaylistRepository,
    private val systemService: SystemService,
    @Assisted private val input: Input,
    @Assisted private val output: Output,
) : MviViewModel<PlaylistDetailUiState, PlaylistDetailUiAction>() {

    data class Input(
        val playlistId: Long
    )

    data class Output(
        val navigateUp: () -> Unit,
        val playVideos: (uris: List<Uri>, startUri: Uri) -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(
            input: Input,
            output: Output,
        ): PlaylistDetailViewModel
    }

    private val internalState = MutableStateFlow(PlaylistDetailUiState())
    override val state: StateFlow<PlaylistDetailUiState> = internalState.asStateFlow()

    init {
        viewModelScope.launch {
            observePlaylist(input.playlistId).collect { playlist ->
                internalState.update { currentState ->
                    currentState.copy(
                        playlistDataState = DataState.Success(playlist),
                        isReordering = currentState.isReordering &&
                            (playlist?.items?.size ?: 0) > 1,
                    )
                }
            }
        }
    }

    override fun onAction(action: PlaylistDetailUiAction) {
        when (action) {
            PlaylistDetailUiAction.OnNavigateUpClick -> output.navigateUp()
            PlaylistDetailUiAction.OnSearchClick -> internalState.update { currentState ->
                currentState.copy(isSearching = true, isReordering = false)
            }
            is PlaylistDetailUiAction.OnSearchQueryChange -> internalState.update { currentState ->
                currentState.copy(searchQuery = action.query)
            }
            PlaylistDetailUiAction.OnCloseSearchClick -> internalState.update { currentState ->
                currentState.copy(isSearching = false, searchQuery = "")
            }
            PlaylistDetailUiAction.OnReorderClick -> internalState.update { currentState ->
                currentState.copy(isReordering = true, isSearching = false, searchQuery = "")
            }
            PlaylistDetailUiAction.OnFinishReorderingClick -> internalState.update { currentState ->
                currentState.copy(isReordering = false)
            }
            is PlaylistDetailUiAction.OnPlayVideos -> playVideos(
                uris = action.uris,
                startUri = action.startUri,
            )
            is PlaylistDetailUiAction.ShowRemoveDialogFor -> internalState.update { currentState ->
                currentState.copy(showRemoveDialogFor = action.item)
            }
            PlaylistDetailUiAction.DismissRemoveDialog -> internalState.update { currentState ->
                currentState.copy(showRemoveDialogFor = null)
            }
            is PlaylistDetailUiAction.RemoveVideo -> removeVideo(action.videoUri)
            is PlaylistDetailUiAction.ReplaceOrder -> replaceOrder(action.orderedUris)
        }
    }

    private fun playVideos(uris: List<Uri>, startUri: Uri) {
        markVideoPlayed(startUri.toString())
        output.playVideos(uris, startUri)
    }

    private fun removeVideo(videoUri: String) {
        updatePlaylist(
            block = { playlistRepository.removeVideo(input.playlistId, videoUri) },
            onSuccess = {
                internalState.update { currentState ->
                    currentState.copy(showRemoveDialogFor = null)
                }
            },
        )
    }

    private fun replaceOrder(orderedUris: List<String>) {
        updatePlaylist(
            block = { playlistRepository.replaceOrder(input.playlistId, orderedUris) },
        )
    }

    private fun markVideoPlayed(videoUri: String) {
        viewModelScope.launch {
            try {
                playlistRepository.markVideoPlayed(input.playlistId, videoUri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Playback should still start if saving the resume item fails.
            }
        }
    }

    private fun updatePlaylist(
        block: suspend () -> Unit,
        onSuccess: () -> Unit = {},
    ) {
        if (state.value.updateActionState.isRunning) return
        viewModelScope.launch {
            internalState.update { currentState ->
                currentState.copy(updateActionState = ActionState.Running)
            }
            try {
                block()
                onSuccess()
                internalState.update { currentState ->
                    currentState.copy(updateActionState = ActionState.Success)
                }
            } catch (cancellation: CancellationException) {
                internalState.update { currentState ->
                    currentState.copy(updateActionState = ActionState.Idle)
                }
                throw cancellation
            } catch (error: Throwable) {
                val message = systemService.getString(R.string.playlist_update_failed)
                internalState.update { currentState ->
                    currentState.copy(
                        updateActionState = ActionState.Failed(
                            Error(message, error),
                        ),
                    )
                }
                systemService.showToast(message, Toast.LENGTH_SHORT)
            }
        }
    }
}

data class PlaylistDetailUiState(
    val playlistDataState: DataState<Playlist?> = DataState.Loading,
    val updateActionState: ActionState = ActionState.Idle,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val isReordering: Boolean = false,
    val showRemoveDialogFor: PlaylistItem? = null,
)

sealed interface PlaylistDetailUiAction {
    data object OnNavigateUpClick : PlaylistDetailUiAction
    data object OnSearchClick : PlaylistDetailUiAction
    data class OnSearchQueryChange(val query: String) : PlaylistDetailUiAction
    data object OnCloseSearchClick : PlaylistDetailUiAction
    data object OnReorderClick : PlaylistDetailUiAction
    data object OnFinishReorderingClick : PlaylistDetailUiAction
    data class OnPlayVideos(
        val uris: List<Uri>,
        val startUri: Uri,
    ) : PlaylistDetailUiAction
    data class ShowRemoveDialogFor(val item: PlaylistItem) : PlaylistDetailUiAction
    data object DismissRemoveDialog : PlaylistDetailUiAction
    data class RemoveVideo(val videoUri: String) : PlaylistDetailUiAction
    data class ReplaceOrder(val orderedUris: List<String>) : PlaylistDetailUiAction
}
