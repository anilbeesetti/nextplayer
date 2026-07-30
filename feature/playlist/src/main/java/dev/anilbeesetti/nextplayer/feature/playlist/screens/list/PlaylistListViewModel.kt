package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistNameConflictException
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistFileGrant
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistFileGrantRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext

const val PLAYLIST_NAME_CONFLICT_MESSAGE = "A playlist with this name already exists."
private const val PLAYLIST_CREATE_ERROR_MESSAGE = "Couldn't create playlist. Try again."
private const val FILE_PERMISSION_EXPIRED_MESSAGE = "File permission expired. Choose the file again."

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
        val preparedGrant: PlaylistFileGrant? = null,
    ) : PlaylistListAction

    data class Delete(val id: Long) : PlaylistListAction

    data object ClearFormError : PlaylistListAction
}

sealed interface PlaylistListEvent {
    data class Created(val playlistId: Long) : PlaylistListEvent

    data class Message(val text: String) : PlaylistListEvent

    data class FileCreationFailed(val text: String) : PlaylistListEvent
}

private data class FormState(
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val fileGrantRepository: PlaylistFileGrantRepository,
) : ViewModel() {
    private val formState = MutableStateFlow(FormState())
    private val createMutex = Mutex()
    private var activeCreation: Job? = null
    private val eventChannel = Channel<PlaylistListEvent>(Channel.BUFFERED)
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
            is PlaylistListAction.CreateLinked -> create(action.preparedGrant) {
                repository.createLinked(action.name, action.type, action.source).playlistId
            }
            is PlaylistListAction.Delete -> delete(action.id)
            PlaylistListAction.ClearFormError -> formState.update { it.copy(error = null) }
        }
    }

    suspend fun acquireFileGrant(uri: String): PlaylistFileGrant? = fileGrantRepository.acquire(uri)

    fun releaseFileGrant(grant: PlaylistFileGrant) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { fileGrantRepository.release(grant) }
        }
    }

    fun cancelCreation() {
        activeCreation?.cancel()
    }

    private fun create(
        preparedGrant: PlaylistFileGrant? = null,
        block: suspend () -> Long,
    ) {
        activeCreation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var createReservation: PlaylistFileGrant? = null
            try {
                createReservation = preparedGrant?.let { grant -> fileGrantRepository.reserve(grant) }
                if (preparedGrant != null && createReservation == null) {
                    throw IllegalStateException(FILE_PERMISSION_EXPIRED_MESSAGE)
                }
                createMutex.withLock {
                    formState.value = FormState(isSaving = true)
                    val playlistId = block()
                    withContext(NonCancellable) {
                        createReservation?.let { fileGrantRepository.retain(it) }
                        preparedGrant?.let { fileGrantRepository.retain(it) }
                    }
                    formState.value = FormState()
                    eventChannel.send(PlaylistListEvent.Created(playlistId))
                }
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    createReservation?.let { fileGrantRepository.release(it) }
                    preparedGrant?.let { fileGrantRepository.release(it) }
                }
                formState.value = FormState()
                throw error
            } catch (_: PlaylistNameConflictException) {
                withContext(NonCancellable) {
                    createReservation?.let { fileGrantRepository.release(it) }
                    preparedGrant?.let { fileGrantRepository.release(it) }
                }
                if (preparedGrant == null) {
                    formState.value = FormState(error = PLAYLIST_NAME_CONFLICT_MESSAGE)
                } else {
                    formState.value = FormState()
                    eventChannel.send(PlaylistListEvent.FileCreationFailed(PLAYLIST_NAME_CONFLICT_MESSAGE))
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    createReservation?.let { fileGrantRepository.release(it) }
                    preparedGrant?.let { fileGrantRepository.release(it) }
                }
                if (preparedGrant == null) {
                    formState.value = FormState(error = error.userMessage())
                } else {
                    formState.value = FormState()
                    eventChannel.send(PlaylistListEvent.FileCreationFailed(error.userMessage()))
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
