package dev.anilbeesetti.nextplayer.feature.playlist.screens.detail

import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.data.playlist.M3UParser
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.domain.ObservePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylist
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistItem
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.ActionState
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class PlaylistDetailViewModel(
    observePlaylist: ObservePlaylistUseCase,
    private val playlistRepository: PlaylistRepository,
    private val m3uParser: M3UParser,
    private val systemService: SystemService,
    @InjectedParam private val input: Input,
    @InjectedParam internal var output: Output,
) : MviViewModel<PlaylistDetailUiState, PlaylistDetailUiAction>() {

    data class Input(
        val playlistId: Long,
    )

    data class Output(
        val navigateUp: () -> Unit,
        val playPlaylist: (playlistId: Long, startUri: Uri) -> Unit,
    )

    private val stateInternal = MutableStateFlow(PlaylistDetailUiState())
    override val state: StateFlow<PlaylistDetailUiState> = combine(
        stateInternal,
        observePlaylist(input.playlistId)
            .onEach { playlist ->
                if (playlist?.type != PlaylistType.LOCAL || playlist.items.size <= 1) {
                    stateInternal.update { it.copy(isReordering = false) }
                }
            }
            .map<Playlist?, DataState<Playlist?>> { DataState.Success(it) }
            .onStart { emit(DataState.Loading) }
            .catch { emit(DataState.Error(it)) },
    ) { state, playlist ->
        state.copy(playlistDataState = playlist)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000L),
        initialValue = stateInternal.value,
    )

    private var refreshJob: Job? = null

    override fun onAction(action: PlaylistDetailUiAction) {
        when (action) {
            is PlaylistDetailUiAction.OnNavigateUpClick -> output.navigateUp()
            is PlaylistDetailUiAction.OnSearchClick -> stateInternal.update {
                it.copy(isSearching = true, isReordering = false)
            }
            is PlaylistDetailUiAction.OnSearchQueryChange -> stateInternal.update {
                it.copy(searchQuery = action.query)
            }
            is PlaylistDetailUiAction.OnCloseSearchClick -> stateInternal.update {
                it.copy(isSearching = false, searchQuery = "")
            }
            is PlaylistDetailUiAction.OnReorderClick -> {
                if (currentPlaylist()?.type == PlaylistType.LOCAL) {
                    stateInternal.update {
                        it.copy(isReordering = true, isSearching = false, searchQuery = "")
                    }
                }
            }
            is PlaylistDetailUiAction.OnFinishReorderingClick -> stateInternal.update {
                it.copy(isReordering = false)
            }
            is PlaylistDetailUiAction.OnPlay -> play(action.startUri)
            is PlaylistDetailUiAction.Refresh -> refreshM3U()
            is PlaylistDetailUiAction.ShowRemoveDialogFor -> stateInternal.update {
                it.copy(showRemoveDialogFor = action.item)
            }
            is PlaylistDetailUiAction.DismissRemoveDialog -> stateInternal.update {
                it.copy(showRemoveDialogFor = null)
            }
            is PlaylistDetailUiAction.RemoveVideo -> removeVideo(action.videoUri)
            is PlaylistDetailUiAction.ReplaceOrder -> replaceOrder(action.orderedUris)
        }
    }

    private fun play(startUri: Uri) {
        markVideoPlayed(startUri.toString())
        output.playPlaylist(input.playlistId, startUri)
    }

    private fun refreshM3U() {
        val playlist = currentPlaylist() ?: return
        if (playlist.type == PlaylistType.LOCAL || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            stateInternal.update { it.copy(isRefreshing = true) }
            try {
                val parsed = parseLinkedSource(playlist).getOrThrow()
                playlistRepository.replaceM3UItems(input.playlistId, parsed.items)
                showToast(systemService.getString(R.string.playlist_refresh_succeeded))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                showToast(
                    error.message?.takeIf(String::isNotBlank)
                        ?: systemService.getString(R.string.playlist_refresh_failed),
                )
            } finally {
                stateInternal.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun parseLinkedSource(playlist: Playlist): Result<M3UPlaylist> {
        val source = playlist.source
            ?: return Result.failure(IllegalStateException("Linked playlist source is missing"))
        return when (playlist.type) {
            PlaylistType.M3U_URL -> m3uParser.parseUrl(source)
            PlaylistType.M3U_FILE -> m3uParser.parseUri(source.toUri())
            PlaylistType.LOCAL -> Result.failure(
                IllegalStateException("Local playlists do not have a linked source"),
            )
        }
    }

    private fun removeVideo(videoUri: String) {
        updatePlaylist(
            block = { playlistRepository.removeVideo(input.playlistId, videoUri) },
            onSuccess = {
                stateInternal.update { it.copy(showRemoveDialogFor = null) }
            },
        )
    }

    private fun replaceOrder(orderedUris: List<String>) {
        updatePlaylist(
            block = {
                playlistRepository.replaceOrder(input.playlistId, orderedUris)
            },
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
        if (stateInternal.value.updateActionState.isRunning) return
        viewModelScope.launch {
            stateInternal.update { it.copy(updateActionState = ActionState.Running) }
            try {
                block()
                onSuccess()
                stateInternal.update { it.copy(updateActionState = ActionState.Success) }
            } catch (cancellation: CancellationException) {
                stateInternal.update { it.copy(updateActionState = ActionState.Idle) }
                throw cancellation
            } catch (error: Throwable) {
                val message = systemService.getString(R.string.playlist_update_failed)
                stateInternal.update {
                    it.copy(updateActionState = ActionState.Failed(Error(message, error)))
                }
                showToast(message)
            }
        }
    }

    private fun currentPlaylist(): Playlist? =
        (state.value.playlistDataState as? DataState.Success)?.value

    private fun showToast(message: String) {
        systemService.showToast(message, Toast.LENGTH_SHORT)
    }
}

data class PlaylistDetailUiState(
    val playlistDataState: DataState<Playlist?> = DataState.Loading,
    val updateActionState: ActionState = ActionState.Idle,
    val isRefreshing: Boolean = false,
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
    data class OnPlay(val startUri: Uri) : PlaylistDetailUiAction
    data object Refresh : PlaylistDetailUiAction
    data class ShowRemoveDialogFor(val item: PlaylistItem) : PlaylistDetailUiAction
    data object DismissRemoveDialog : PlaylistDetailUiAction
    data class RemoveVideo(val videoUri: String) : PlaylistDetailUiAction
    data class ReplaceOrder(val orderedUris: List<String>) : PlaylistDetailUiAction
}
