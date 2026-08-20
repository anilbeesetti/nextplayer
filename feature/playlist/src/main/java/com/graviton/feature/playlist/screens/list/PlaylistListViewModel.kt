package com.graviton.feature.playlist.screens.list

import android.widget.Toast
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import com.graviton.core.common.service.system.SystemService
import com.graviton.core.data.repository.PlaylistRepository
import com.graviton.core.media.sync.MediaSynchronizer
import com.graviton.core.model.PlaylistSummary
import com.graviton.core.ui.R
import com.graviton.core.ui.base.ActionState
import com.graviton.core.ui.base.DataState
import com.graviton.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlaylistListViewModel.Factory::class)
class PlaylistListViewModel @AssistedInject constructor(
    private val playlistRepository: PlaylistRepository,
    private val mediaSynchronizer: MediaSynchronizer,
    private val systemService: SystemService,
    @Assisted private val output: Output,
) : MviViewModel<PlaylistListUiState, PlaylistUiAction>() {

    data class Output(
        val openPlaylist: (Long) -> Unit,
        val openSettings: () -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(output: Output): PlaylistListViewModel
    }

    private val internalState = MutableStateFlow(PlaylistListUiState())
    override val state: StateFlow<PlaylistListUiState> = internalState.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { playlists ->
                internalState.update { currentState ->
                    currentState.copy(playlistsDataState = DataState.Success(playlists))
                }
            }
        }
    }

    override fun onAction(action: PlaylistUiAction) {
        when (action) {
            is PlaylistUiAction.OnSettingsClick -> output.openSettings()
            is PlaylistUiAction.OnPlaylistClick -> output.openPlaylist(action.playlist.id)
            is PlaylistUiAction.ShowCreateDialog -> internalState.update { currentState ->
                currentState.copy(showCreateDialog = true, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.DismissCreateDialog -> internalState.update { currentState ->
                currentState.copy(showCreateDialog = false, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.ShowRenameDialogFor -> internalState.update { currentState ->
                currentState.copy(showRenameDialogFor = action.playlist, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.DismissRenameDialog -> internalState.update { currentState ->
                currentState.copy(showRenameDialogFor = null, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.ShowDeleteDialogFor -> internalState.update { currentState ->
                currentState.copy(showDeleteDialogFor = action.playlist, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.DismissDeleteDialog -> internalState.update { currentState ->
                currentState.copy(showDeleteDialogFor = null, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.Create -> create(action.name)
            is PlaylistUiAction.Rename -> rename(action.playlistId, action.name)
            is PlaylistUiAction.Delete -> delete(action.playlistId)
        }
    }

    fun synchronize() {
        mediaSynchronizer.startSync()
    }

    private fun create(name: String) {
        save {
            val playlistId = playlistRepository.create(name)
            internalState.update { currentState ->
                currentState.copy(showCreateDialog = false)
            }
            output.openPlaylist(playlistId)
        }
    }

    private fun rename(playlistId: Long, name: String) {
        save {
            playlistRepository.rename(playlistId, name)
            internalState.update { currentState ->
                currentState.copy(showRenameDialogFor = null)
            }
        }
    }

    private fun delete(playlistId: Long) {
        viewModelScope.launch {
            try {
                playlistRepository.delete(playlistId)
                internalState.update { currentState ->
                    currentState.copy(showDeleteDialogFor = null)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                systemService.showToast(
                    text = systemService.getString(R.string.playlist_delete_failed),
                    duration = Toast.LENGTH_SHORT,
                )
            }
        }
    }

    private fun save(block: suspend () -> Unit) {
        if (state.value.saveActionState.isRunning) return
        viewModelScope.launch {
            internalState.update { currentState ->
                currentState.copy(saveActionState = ActionState.Running)
            }
            try {
                block()
                internalState.update { currentState ->
                    currentState.copy(saveActionState = ActionState.Success)
                }
            } catch (cancellation: CancellationException) {
                internalState.update { currentState ->
                    currentState.copy(saveActionState = ActionState.Idle)
                }
                throw cancellation
            } catch (error: Throwable) {
                internalState.update { currentState ->
                    currentState.copy(
                        saveActionState = ActionState.Failed(
                            value = Error(
                                systemService.getString(R.string.playlist_save_failed),
                                error
                            )
                        )
                    )
                }
            }
        }
    }
}

data class PlaylistListUiState(
    val playlistsDataState: DataState<List<PlaylistSummary>> = DataState.Loading,
    val saveActionState: ActionState = ActionState.Idle,
    val showCreateDialog: Boolean = false,
    val showRenameDialogFor: PlaylistSummary? = null,
    val showDeleteDialogFor: PlaylistSummary? = null,
)

sealed interface PlaylistUiAction {
    data object OnSettingsClick : PlaylistUiAction
    data class OnPlaylistClick(val playlist: PlaylistSummary) : PlaylistUiAction
    data object ShowCreateDialog : PlaylistUiAction
    data object DismissCreateDialog: PlaylistUiAction
    data class ShowRenameDialogFor(val playlist: PlaylistSummary) : PlaylistUiAction
    data object DismissRenameDialog : PlaylistUiAction
    data class ShowDeleteDialogFor(val playlist: PlaylistSummary) : PlaylistUiAction
    data object DismissDeleteDialog : PlaylistUiAction
    data class Create(val name: String) : PlaylistUiAction
    data class Rename(val playlistId: Long, val name: String) : PlaylistUiAction
    data class Delete(val playlistId: Long) : PlaylistUiAction
}

sealed interface PlaylistListEvent {
    data class Created(val playlistId: Long) : PlaylistListEvent
    data class Message(val messageRes: Int) : PlaylistListEvent
}
