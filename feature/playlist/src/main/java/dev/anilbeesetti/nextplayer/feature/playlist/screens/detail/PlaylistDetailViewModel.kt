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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal const val EDITABLE_REFRESH_MESSAGE = "Editable playlists don't have a linked source."
internal const val LINKED_MOVE_MESSAGE = "Linked playlists use their source order."
private const val PLAYLIST_ACTION_ERROR_MESSAGE = "Couldn't update playlist. Try again."
private const val MOVE_RECONCILIATION_TIMEOUT_MILLIS = 2_000L

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMoving: Boolean = false,
)

sealed interface PlaylistDetailAction {
    data object PlayAll : PlaylistDetailAction

    data class PlayItem(val uri: String) : PlaylistDetailAction

    data object Refresh : PlaylistDetailAction

    data class MoveItem(val uri: String, val toIndex: Int) : PlaylistDetailAction

    data object StartMoveDrag : PlaylistDetailAction

    data class PreviewMove(val fromIndex: Int, val toIndex: Int) : PlaylistDetailAction

    data object StopMoveDrag : PlaylistDetailAction

}

sealed interface PlaylistDetailEvent {
    data class Play(val playlistId: Long, val startUri: String) : PlaylistDetailEvent

    data class Message(val text: String) : PlaylistDetailEvent
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
    private val eventChannel = Channel<PlaylistDetailEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var refreshJob: Job? = null
    private var moveJob: Job? = null
    private var moveReconciliationJob: Job? = null
    private val repositoryPlaylist = MutableStateFlow<Playlist?>(null)
    private val hasLoaded = MutableStateFlow(false)
    private val reorderState = PlaylistReorderState()

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        repositoryPlaylist,
        hasLoaded,
        refreshState,
        reorderState.state,
    ) { playlist, loaded, refreshing, reorder ->
        PlaylistDetailUiState(
            playlist = playlist?.copy(items = reorder.displayedItems),
            isLoading = !loaded,
            isRefreshing = refreshing,
            isMoving = reorder.isMoving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PlaylistDetailUiState(),
    )

    init {
        viewModelScope.launch {
            repository.observePlaylist(playlistId).collect { playlist ->
                if (reorderState.updateRepositoryItems(playlist?.items.orEmpty())) {
                    moveReconciliationJob?.cancel()
                    moveReconciliationJob = null
                }
                repositoryPlaylist.value = playlist
                hasLoaded.value = true
            }
        }
    }

    fun onAction(action: PlaylistDetailAction) {
        when (action) {
            PlaylistDetailAction.PlayAll -> play(
                startUri = reorderState.state.value.displayedItems.firstOrNull()?.uriString,
            )
            is PlaylistDetailAction.PlayItem -> play(startUri = action.uri)
            PlaylistDetailAction.Refresh -> refresh()
            is PlaylistDetailAction.MoveItem -> moveItem(action.uri, action.toIndex)
            PlaylistDetailAction.StartMoveDrag -> startMoveDrag()
            is PlaylistDetailAction.PreviewMove -> previewMove(action.fromIndex, action.toIndex)
            PlaylistDetailAction.StopMoveDrag -> stopMoveDrag()
        }
    }

    private fun play(startUri: String?) {
        val reorderSnapshot = reorderState.state.value
        if (reorderSnapshot.isDragging || reorderSnapshot.isMoving) return
        val uris = reorderSnapshot.displayedItems.map { it.uriString }
        if (startUri == null || startUri !in uris) return
        viewModelScope.launch {
            eventChannel.send(PlaylistDetailEvent.Play(playlistId = playlistId, startUri = startUri))
        }
    }

    private fun refresh() {
        val playlist = repositoryPlaylist.value ?: return
        if (playlist.type == PlaylistType.EDITABLE) {
            sendMessage(EDITABLE_REFRESH_MESSAGE)
            return
        }
        if (refreshJob != null) return

        refreshState.value = true
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = repository.refresh(playlistId)
                eventChannel.send(PlaylistDetailEvent.Message(result.userMessage()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                eventChannel.send(PlaylistDetailEvent.Message(error.userMessage()))
            } finally {
                if (refreshJob === job) refreshJob = null
                refreshState.value = false
            }
        }
        refreshJob = job
        job.start()
    }

    private fun moveItem(uri: String, toIndex: Int) {
        val playlist = repositoryPlaylist.value ?: return
        if (playlist.type != PlaylistType.EDITABLE) {
            sendMessage(LINKED_MOVE_MESSAGE)
            return
        }
        val move = reorderState.startMove(uri, toIndex) ?: return
        persistMove(move)
    }

    private fun startMoveDrag() {
        if (repositoryPlaylist.value?.type != PlaylistType.EDITABLE) return
        reorderState.startDrag()
    }

    private fun previewMove(fromIndex: Int, toIndex: Int) {
        if (repositoryPlaylist.value?.type != PlaylistType.EDITABLE) return
        reorderState.move(fromIndex, toIndex)
    }

    private fun stopMoveDrag() {
        val move = reorderState.stopDragAndStartMove() ?: return
        persistMove(move)
    }

    private fun persistMove(move: PlaylistMove) {
        if (moveJob != null) return
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var succeeded = false
            try {
                repository.moveItem(playlistId, move.uri, move.toIndex)
                succeeded = true
                if (moveJob === job) moveJob = null
                if (reorderState.moveSucceeded()) startMoveReconciliationTimeout()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                eventChannel.send(PlaylistDetailEvent.Message(error.userMessage()))
            } finally {
                if (!succeeded) {
                    if (moveJob === job) moveJob = null
                    reorderState.finishMove()
                }
            }
        }
        moveJob = job
        job.start()
    }

    private fun startMoveReconciliationTimeout() {
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            delay(MOVE_RECONCILIATION_TIMEOUT_MILLIS)
            if (moveReconciliationJob === job) {
                moveReconciliationJob = null
                reorderState.reconcileTimedOut()
            }
        }
        moveReconciliationJob = job
        job.start()
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
