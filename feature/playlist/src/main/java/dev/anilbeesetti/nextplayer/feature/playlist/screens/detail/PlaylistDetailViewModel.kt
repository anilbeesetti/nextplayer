package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.domain.ObservePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.SyncPlaylistsWithMediaUseCase
import dev.anilbeesetti.nextplayer.core.model.Playlist
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

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val isLoading: Boolean = true,
    val actionsEnabled: Boolean = false,
)

sealed interface PlaylistDetailEvent {
    data class Message(val text: String) : PlaylistDetailEvent
}

private data class DetailOperationState(
    val isSaving: Boolean = false,
)

@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    @Assisted private val playlistId: Long,
    observePlaylist: ObservePlaylistUseCase,
    private val playlistRepository: PlaylistRepository,
    private val syncPlaylistsWithMedia: SyncPlaylistsWithMediaUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(playlistId: Long): PlaylistDetailViewModel
    }

    private val operationState = MutableStateFlow(DetailOperationState())
    private val eventChannel = Channel<PlaylistDetailEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var syncJob: Job? = null

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        observePlaylist(playlistId),
        operationState,
    ) { resolvedPlaylist, operation ->
        PlaylistDetailUiState(
            playlist = resolvedPlaylist,
            isLoading = false,
            actionsEnabled = !operation.isSaving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistDetailUiState(),
    )

    fun synchronize() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            syncPlaylistsWithMedia()
        }
    }

    fun removeVideo(videoUri: String) {
        updatePlaylist { playlistRepository.removeVideo(playlistId, videoUri) }
    }

    fun replaceOrder(orderedUris: List<String>) {
        updatePlaylist { playlistRepository.replaceOrder(playlistId, orderedUris) }
    }

    fun markVideoPlayed(videoUri: String) {
        viewModelScope.launch {
            try {
                playlistRepository.markVideoPlayed(playlistId, videoUri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Playback should still start if saving the resume item fails.
            }
        }
    }

    private fun updatePlaylist(block: suspend () -> Unit) {
        if (operationState.value.isSaving) return
        viewModelScope.launch {
            operationState.update { it.copy(isSaving = true) }
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                eventChannel.send(PlaylistDetailEvent.Message("Couldn't update playlist. Try again."))
            } finally {
                operationState.update { it.copy(isSaving = false) }
            }
        }
    }
}
