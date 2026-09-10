package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.extensions.collectWhileSubscribed
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.data.playlist.M3UDocumentPermissionManager
import dev.anilbeesetti.nextplayer.core.data.playlist.M3UParser
import dev.anilbeesetti.nextplayer.core.data.playlist.PersistedM3UGrant
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import dev.anilbeesetti.nextplayer.core.model.M3UPlaylist
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.ActionState
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlaylistListViewModel.Factory::class)
class PlaylistListViewModel @AssistedInject constructor(
    private val playlistRepository: PlaylistRepository,
    private val m3uParser: M3UParser,
    private val documentPermissionManager: M3UDocumentPermissionManager,
    private val mediaSynchronizer: MediaSynchronizer,
    private val systemService: SystemService,
    @Assisted internal var output: Output,
) : MviViewModel<PlaylistListUiState, PlaylistUiAction>() {

    data class Output(
        val openPlaylist: (Long) -> Unit,
        val openSettings: () -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(output: Output): PlaylistListViewModel
    }

    private var linkedCreationJob: Job? = null

    private val stateInternal = MutableStateFlow(PlaylistListUiState())
    override val state: StateFlow<PlaylistListUiState> = stateInternal.asStateFlow()

    init {
        playlistRepository.observePlaylists().collectWhileSubscribed(viewModelScope, stateInternal) { playlists ->
            stateInternal.update {
                it.copy(playlistsDataState = DataState.Success(playlists))
            }
        }
    }

    override fun onAction(action: PlaylistUiAction) {
        when (action) {
            is PlaylistUiAction.Synchronize -> synchronize()
            is PlaylistUiAction.CreateM3UFile -> createM3UFile(action.uri)

            is PlaylistUiAction.OnSettingsClick -> output.openSettings()
            is PlaylistUiAction.OnPlaylistClick -> output.openPlaylist(action.playlist.id)
            is PlaylistUiAction.ShowCreationChooser -> showCreationDialog(PlaylistCreationDialog.CHOOSER)
            is PlaylistUiAction.ChooseLocalPlaylist -> showCreationDialog(PlaylistCreationDialog.LOCAL_NAME)
            is PlaylistUiAction.ChooseM3UUrl -> showCreationDialog(PlaylistCreationDialog.M3U_URL)
            is PlaylistUiAction.DismissCreation -> showCreationDialog(PlaylistCreationDialog.NONE)
            is PlaylistUiAction.ShowRenameDialogFor -> stateInternal.update {
                it.copy(showRenameDialogFor = action.playlist, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.DismissRenameDialog -> stateInternal.update {
                it.copy(showRenameDialogFor = null, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.ShowDeleteDialogFor -> stateInternal.update {
                it.copy(showDeleteDialogFor = action.playlist, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.DismissDeleteDialog -> stateInternal.update {
                it.copy(showDeleteDialogFor = null, saveActionState = ActionState.Idle)
            }
            is PlaylistUiAction.CreateLocal -> createLocal(action.name)
            is PlaylistUiAction.CreateM3UUrl -> createM3UUrl(action.url)
            is PlaylistUiAction.Rename -> rename(action.playlistId, action.name)
            is PlaylistUiAction.Delete -> delete(action.playlistId)
        }
    }

    private fun synchronize() {
        mediaSynchronizer.startSync()
    }

    private fun createM3UFile(uri: Uri) {
        if (linkedCreationJob?.isActive == true) return
        linkedCreationJob = viewModelScope.launch {
            val grant = documentPermissionManager.acquire(uri).getOrElse {
                showToast(R.string.m3u_file_permission_failed)
                return@launch
            }
            createM3U(
                type = PlaylistType.M3U_FILE,
                source = uri.toString(),
                grant = grant,
                showInlineError = false,
            ) {
                m3uParser.parseUri(uri)
            }
        }
    }

    private fun showCreationDialog(dialog: PlaylistCreationDialog) {
        stateInternal.update {
            it.copy(creationDialog = dialog, saveActionState = ActionState.Idle)
        }
    }

    private fun createLocal(name: String) {
        save {
            val playlistId = playlistRepository.create(name)
            stateInternal.update { it.copy(creationDialog = PlaylistCreationDialog.NONE) }
            output.openPlaylist(playlistId)
        }
    }

    private fun createM3UUrl(url: String) {
        if (linkedCreationJob?.isActive == true) return
        val source = url.trim()
        linkedCreationJob = viewModelScope.launch {
            createM3U(
                type = PlaylistType.M3U_URL,
                source = source,
                showInlineError = true,
            ) {
                m3uParser.parseUrl(source)
            }
        }
    }

    private suspend fun createM3U(
        type: PlaylistType,
        source: String,
        grant: PersistedM3UGrant? = null,
        showInlineError: Boolean,
        parse: suspend () -> Result<M3UPlaylist>,
    ) {
        stateInternal.update { it.copy(saveActionState = ActionState.Running) }
        try {
            val playlist = parse().getOrThrow()
            val playlistId = playlistRepository.createM3U(type, source, playlist)
            stateInternal.update {
                it.copy(
                    creationDialog = PlaylistCreationDialog.NONE,
                    saveActionState = ActionState.Success,
                )
            }
            output.openPlaylist(playlistId)
        } catch (cancellation: CancellationException) {
            grant?.let(documentPermissionManager::release)
            stateInternal.update { it.copy(saveActionState = ActionState.Idle) }
            throw cancellation
        } catch (error: Throwable) {
            grant?.let(documentPermissionManager::release)
            val message = error.message?.takeIf(String::isNotBlank)
                ?: systemService.getString(R.string.playlist_save_failed)
            if (showInlineError) {
                stateInternal.update {
                    it.copy(saveActionState = ActionState.Failed(Error(message, error)))
                }
            } else {
                stateInternal.update {
                    it.copy(
                        creationDialog = PlaylistCreationDialog.NONE,
                        saveActionState = ActionState.Idle,
                    )
                }
                systemService.showToast(message, Toast.LENGTH_SHORT)
            }
        }
    }

    private fun rename(playlistId: Long, name: String) {
        save {
            playlistRepository.rename(playlistId, name)
            stateInternal.update { it.copy(showRenameDialogFor = null) }
        }
    }

    private fun delete(playlistId: Long) {
        viewModelScope.launch {
            try {
                val playlist = playlistRepository.getPlaylist(playlistId)
                playlistRepository.delete(playlistId)
                if (
                    playlist?.type == PlaylistType.M3U_FILE &&
                    playlistRepository.countFilePlaylistsBySource(playlist.source.orEmpty()) == 0
                ) {
                    playlist.source?.let(Uri::parse)?.let(documentPermissionManager::release)
                }
                stateInternal.update { it.copy(showDeleteDialogFor = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                showToast(R.string.playlist_delete_failed)
            }
        }
    }

    private fun save(block: suspend () -> Unit) {
        if (state.value.saveActionState.isRunning) return
        viewModelScope.launch {
            stateInternal.update { it.copy(saveActionState = ActionState.Running) }
            try {
                block()
                stateInternal.update { it.copy(saveActionState = ActionState.Success) }
            } catch (cancellation: CancellationException) {
                stateInternal.update { it.copy(saveActionState = ActionState.Idle) }
                throw cancellation
            } catch (error: Throwable) {
                stateInternal.update {
                    it.copy(
                        saveActionState = ActionState.Failed(
                            Error(systemService.getString(R.string.playlist_save_failed), error),
                        ),
                    )
                }
            }
        }
    }

    private fun showToast(messageRes: Int) {
        systemService.showToast(
            text = systemService.getString(messageRes),
            duration = Toast.LENGTH_SHORT,
        )
    }
}

enum class PlaylistCreationDialog {
    NONE,
    CHOOSER,
    LOCAL_NAME,
    M3U_URL,
}

data class PlaylistListUiState(
    val playlistsDataState: DataState<List<PlaylistSummary>> = DataState.Loading,
    val saveActionState: ActionState = ActionState.Idle,
    val creationDialog: PlaylistCreationDialog = PlaylistCreationDialog.NONE,
    val showRenameDialogFor: PlaylistSummary? = null,
    val showDeleteDialogFor: PlaylistSummary? = null,
)

sealed interface PlaylistUiAction {
    data object Synchronize : PlaylistUiAction
    data class CreateM3UFile(val uri: Uri) : PlaylistUiAction

    data object OnSettingsClick : PlaylistUiAction
    data class OnPlaylistClick(val playlist: PlaylistSummary) : PlaylistUiAction
    data object ShowCreationChooser : PlaylistUiAction
    data object ChooseLocalPlaylist : PlaylistUiAction
    data object ChooseM3UUrl : PlaylistUiAction
    data object DismissCreation : PlaylistUiAction
    data class ShowRenameDialogFor(val playlist: PlaylistSummary) : PlaylistUiAction
    data object DismissRenameDialog : PlaylistUiAction
    data class ShowDeleteDialogFor(val playlist: PlaylistSummary) : PlaylistUiAction
    data object DismissDeleteDialog : PlaylistUiAction
    data class CreateLocal(val name: String) : PlaylistUiAction
    data class CreateM3UUrl(val url: String) : PlaylistUiAction
    data class Rename(val playlistId: Long, val name: String) : PlaylistUiAction
    data class Delete(val playlistId: Long) : PlaylistUiAction
}
