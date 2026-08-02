package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.domain.SyncPlaylistsWithMediaUseCase
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistListUiState(
    val playlists: List<PlaylistSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val formError: String? = null,
    val saveVersion: Long = 0,
)

sealed interface PlaylistListEvent {
    data class Created(val playlistId: Long) : PlaylistListEvent
    data class Message(val text: String) : PlaylistListEvent
}

private data class PlaylistListOperationState(
    val isSaving: Boolean = false,
    val formError: String? = null,
    val saveVersion: Long = 0,
)

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val syncPlaylistsWithMedia: SyncPlaylistsWithMediaUseCase,
) : ViewModel() {

    private val operationState = MutableStateFlow(PlaylistListOperationState())
    private val eventChannel = Channel<PlaylistListEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var syncJob: Job? = null

    val uiState: StateFlow<PlaylistListUiState> = combine(
        playlistRepository.observePlaylists(),
        operationState,
    ) { playlists, operation ->
        PlaylistListUiState(
            playlists = playlists,
            isLoading = false,
            isSaving = operation.isSaving,
            formError = operation.formError,
            saveVersion = operation.saveVersion,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistListUiState(),
    )

    fun synchronize() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            syncPlaylistsWithMedia()
        }
    }

    fun create(name: String) {
        save {
            val playlistId = playlistRepository.create(name)
            eventChannel.send(PlaylistListEvent.Created(playlistId))
        }
    }

    fun rename(playlistId: Long, name: String) {
        save { playlistRepository.rename(playlistId, name) }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch {
            try {
                playlistRepository.delete(playlistId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                eventChannel.send(PlaylistListEvent.Message("Couldn't delete playlist. Try again."))
            }
        }
    }

    fun clearFormError() {
        operationState.update { it.copy(formError = null) }
    }

    private fun save(block: suspend () -> Unit) {
        if (operationState.value.isSaving) return
        viewModelScope.launch {
            operationState.update { it.copy(isSaving = true, formError = null) }
            try {
                block()
                operationState.update {
                    it.copy(
                        isSaving = false,
                        formError = null,
                        saveVersion = it.saveVersion + 1,
                    )
                }
            } catch (cancellation: CancellationException) {
                operationState.update { it.copy(isSaving = false) }
                throw cancellation
            } catch (_: Throwable) {
                operationState.update {
                    it.copy(
                        isSaving = false,
                        formError = "Couldn't save playlist. Try again.",
                    )
                }
            }
        }
    }
}
