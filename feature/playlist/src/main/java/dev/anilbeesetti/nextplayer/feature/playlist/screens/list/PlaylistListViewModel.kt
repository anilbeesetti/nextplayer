package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistNameConflictException
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val PLAYLIST_NAME_CONFLICT_MESSAGE = "A playlist with this name already exists."
private const val PLAYLIST_CREATE_ERROR_MESSAGE = "Couldn't create playlist. Try again."

data class PlaylistListUiState(
    val playlists: List<PlaylistSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val formError: String? = null,
)

sealed interface PlaylistListAction {
    data class CreateEditable(val name: String) : PlaylistListAction

    data class CreateLinked(
        val name: String,
        val type: PlaylistType,
        val source: String,
    ) : PlaylistListAction

    data class Delete(val id: Long) : PlaylistListAction

    data object ClearFormError : PlaylistListAction
}

sealed interface PlaylistListEvent {
    data class Created(val playlistId: Long) : PlaylistListEvent

    data class Message(val text: String) : PlaylistListEvent
}

private data class FormState(
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {
    private val formState = MutableStateFlow(FormState())
    private val createMutex = Mutex()
    private val eventChannel = Channel<PlaylistListEvent>()
    val events = eventChannel.receiveAsFlow()

    private val playlistState = repository.observePlaylists()
        .map { playlists -> PlaylistListUiState(playlists = playlists, isLoading = false) }

    val uiState: StateFlow<PlaylistListUiState> = combine(playlistState, formState) { playlists, form ->
        playlists.copy(isSaving = form.isSaving, formError = form.error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistListUiState(),
    )

    fun onAction(action: PlaylistListAction) {
        when (action) {
            is PlaylistListAction.CreateEditable -> create { repository.createEditable(action.name) }
            is PlaylistListAction.CreateLinked -> create {
                repository.createLinked(action.name, action.type, action.source).playlistId
            }
            is PlaylistListAction.Delete -> delete(action.id)
            PlaylistListAction.ClearFormError -> formState.update { it.copy(error = null) }
        }
    }

    private fun create(block: suspend () -> Long) {
        viewModelScope.launch {
            createMutex.withLock {
                formState.value = FormState(isSaving = true)
                try {
                    val playlistId = block()
                    formState.value = FormState()
                    eventChannel.send(PlaylistListEvent.Created(playlistId))
                } catch (error: CancellationException) {
                    formState.value = FormState()
                    throw error
                } catch (_: PlaylistNameConflictException) {
                    formState.value = FormState(error = PLAYLIST_NAME_CONFLICT_MESSAGE)
                } catch (error: Throwable) {
                    formState.value = FormState(error = error.userMessage())
                }
            }
        }
    }

    private fun delete(id: Long) {
        viewModelScope.launch {
            try {
                repository.delete(id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                eventChannel.send(PlaylistListEvent.Message(error.userMessage()))
            }
        }
    }
}

private fun Throwable.userMessage(): String = generateSequence(this) { it.cause }
    .mapNotNull { it.message?.takeIf(String::isNotBlank) }
    .lastOrNull()
    ?: PLAYLIST_CREATE_ERROR_MESSAGE
