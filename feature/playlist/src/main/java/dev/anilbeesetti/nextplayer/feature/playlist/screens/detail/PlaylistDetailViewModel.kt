package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRefreshResult
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal const val EDITABLE_REFRESH_MESSAGE = "Editable playlists don't have a linked source."
internal const val LINKED_MOVE_MESSAGE = "Linked playlists use their source order."
private const val PLAYLIST_ACTION_ERROR_MESSAGE = "Couldn't update playlist. Try again."

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

sealed interface PlaylistDetailAction {
    data object PlayAll : PlaylistDetailAction

    data class PlayItem(val uri: String) : PlaylistDetailAction

    data object Refresh : PlaylistDetailAction

    data class MoveItem(val uri: String, val toIndex: Int) : PlaylistDetailAction

    data object Delete : PlaylistDetailAction
}

sealed interface PlaylistDetailEvent {
    data class Play(val uris: List<String>, val startUri: String) : PlaylistDetailEvent

    data class Message(val text: String) : PlaylistDetailEvent

    data object Deleted : PlaylistDetailEvent
}

@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    @Assisted private val playlistId: Long,
    private val repository: PlaylistRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playlistId: Long): PlaylistDetailViewModel
    }

    private val refreshState = MutableStateFlow(false)
    private val eventChannel = Channel<PlaylistDetailEvent>()
    val events = eventChannel.receiveAsFlow()

    private val playlistState = repository.observePlaylist(playlistId)
        .map { playlist -> PlaylistDetailUiState(playlist = playlist, isLoading = false) }

    val uiState: StateFlow<PlaylistDetailUiState> = combine(playlistState, refreshState) { playlist, refreshing ->
        playlist.copy(isRefreshing = refreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistDetailUiState(),
    )

    fun onAction(action: PlaylistDetailAction) {
        when (action) {
            PlaylistDetailAction.PlayAll -> play(startUri = uiState.value.playlist?.items?.firstOrNull()?.uriString)
            is PlaylistDetailAction.PlayItem -> play(startUri = action.uri)
            PlaylistDetailAction.Refresh -> refresh()
            is PlaylistDetailAction.MoveItem -> moveItem(action.uri, action.toIndex)
            PlaylistDetailAction.Delete -> delete()
        }
    }

    private fun play(startUri: String?) {
        val uris = uiState.value.playlist?.items?.map { it.uriString }.orEmpty()
        if (startUri == null || startUri !in uris) return
        viewModelScope.launch {
            eventChannel.send(PlaylistDetailEvent.Play(uris = uris, startUri = startUri))
        }
    }

    private fun refresh() {
        val playlist = uiState.value.playlist ?: return
        if (playlist.type == PlaylistType.EDITABLE) {
            sendMessage(EDITABLE_REFRESH_MESSAGE)
            return
        }
        if (refreshState.value) return

        viewModelScope.launch {
            refreshState.value = true
            try {
                val result = repository.refresh(playlistId)
                eventChannel.send(PlaylistDetailEvent.Message(result.userMessage()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                eventChannel.send(PlaylistDetailEvent.Message(error.userMessage()))
            } finally {
                refreshState.value = false
            }
        }
    }

    private fun moveItem(uri: String, toIndex: Int) {
        val playlist = uiState.value.playlist ?: return
        if (playlist.type != PlaylistType.EDITABLE) {
            sendMessage(LINKED_MOVE_MESSAGE)
            return
        }

        viewModelScope.launch {
            try {
                repository.moveItem(playlistId, uri, toIndex)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                eventChannel.send(PlaylistDetailEvent.Message(error.userMessage()))
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            try {
                repository.delete(playlistId)
                eventChannel.send(PlaylistDetailEvent.Deleted)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                eventChannel.send(PlaylistDetailEvent.Message(error.userMessage()))
            }
        }
    }

    private fun sendMessage(message: String) {
        viewModelScope.launch { eventChannel.send(PlaylistDetailEvent.Message(message)) }
    }
}

private fun PlaylistRefreshResult.userMessage(): String = buildString {
    append("Loaded $itemCount ${if (itemCount == 1) "item" else "items"}")
    if (skippedEntries > 0) {
        append("; skipped $skippedEntries invalid ${if (skippedEntries == 1) "entry" else "entries"}")
    }
    append('.')
}

private fun Throwable.userMessage(): String = generateSequence(this) { it.cause }
    .mapNotNull { it.message?.takeIf(String::isNotBlank) }
    .lastOrNull()
    ?: PLAYLIST_ACTION_ERROR_MESSAGE
