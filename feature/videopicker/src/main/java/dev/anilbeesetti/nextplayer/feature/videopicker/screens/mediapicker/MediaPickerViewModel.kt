package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.extensions.prettyName
import dev.anilbeesetti.nextplayer.core.common.service.system.SystemService
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultPinRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import dev.anilbeesetti.nextplayer.core.domain.GetRecentlyPlayedVideoUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.domain.MediaHolder
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.TransferEvent
import dev.anilbeesetti.nextplayer.core.media.services.TransferMode
import dev.anilbeesetti.nextplayer.core.media.services.TransferProgress
import dev.anilbeesetti.nextplayer.core.media.services.TransferResult
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.findClosestFolder
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = MediaPickerViewModel.Factory::class)
class MediaPickerViewModel @AssistedInject constructor(
    private val getSortedMediaUseCase: GetSortedMediaUseCase,
    private val getRecentlyPlayedVideoUseCase: GetRecentlyPlayedVideoUseCase,
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val mediaOperationsService: MediaOperationsService,
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaSynchronizer: MediaSynchronizer,
    private val vaultRepository: VaultRepository,
    private val vaultPinRepository: VaultPinRepository,
    private val systemService: SystemService,
    @ApplicationContext private val context: Context,
    @Assisted private val input: Input,
    @Assisted internal var output: Output,
) : MviViewModel<MediaPickerUiState, MediaPickerAction>() {

    data class Input(
        val folderId: String?,
    )

    data class Output(
        val navigateUp: () -> Unit,
        val playVideo: (Uri) -> Unit,
        val playVideos: (List<Uri>) -> Unit,
        val openFolder: (String) -> Unit,
        val openSettings: () -> Unit,
        val openSearch: () -> Unit,
        val openVault: () -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(
            input: Input,
            output: Output,
        ): MediaPickerViewModel
    }

    val folderPath = input.folderId

    private val stateInternal = MutableStateFlow(
        MediaPickerUiState(
            folderName = folderPath?.let { File(folderPath).prettyName },
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    override val state: StateFlow<MediaPickerUiState> = stateInternal.asStateFlow()

    private var mediaCollectJob: Job? = null
    private var transferJob: Job? = null

    init {
        if (ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED) {
            startMediaCollection()
        }
        collectPreferences()
        collectPlaylists()
    }

    override fun onAction(action: MediaPickerAction) {
        when (action) {
            is MediaPickerAction.OnNavigateUpClick -> output.navigateUp()
            is MediaPickerAction.OnPlayVideo -> output.playVideo(action.uri)
            is MediaPickerAction.OnFolderClick -> output.openFolder(action.folderPath)
            is MediaPickerAction.OnSettingsClick -> output.openSettings()
            is MediaPickerAction.OnSearchClick -> output.openSearch()
            is MediaPickerAction.OnVaultClick -> output.openVault()
            is MediaPickerAction.Refresh -> refresh()
            is MediaPickerAction.RenameVideo -> renameVideo(action.uri, action.to)
            is MediaPickerAction.UpdateMenu -> updateMenu(action.preferences)
            is MediaPickerAction.OnPermissionAccepted -> startMediaCollection()
            is MediaPickerAction.PlaySelectedItems -> playSelectedItems(action.selectionItems)
            is MediaPickerAction.DeleteSelectedItems -> deleteSelectedItems(action.selectionItems, action.permanently)
            is MediaPickerAction.ShareSelectedItems -> shareSelectedItems(action.selectionItems)
            is MediaPickerAction.ShowMediaInfo -> showMediaInfo(action.video)
            is MediaPickerAction.DismissMediaInfo -> stateInternal.update { it.copy(mediaInfo = null) }
            is MediaPickerAction.CopySelectedItems -> transferSelectedItems(action.selectionItems, TransferMode.COPY)
            is MediaPickerAction.MoveSelectedItems -> transferSelectedItems(action.selectionItems, TransferMode.MOVE)
            is MediaPickerAction.CancelTransfer -> cancelTransfer()
            is MediaPickerAction.RequestHideSelectedItems -> requestHideSelectedItems(action.selectionItems)
            is MediaPickerAction.SetVaultPinAndHide -> setVaultPinAndHide(action.pin)
            is MediaPickerAction.CompleteBiometricSetup -> completeBiometricSetup(action.enabled)
            is MediaPickerAction.ConfirmHidePendingItems -> confirmHidePendingItems()
            is MediaPickerAction.DismissHideFlow -> stateInternal.update { it.copy(hideFlow = HideFlowState.Idle) }
            is MediaPickerAction.ShowAddToPlaylist -> showAddToPlaylist(action.selectionItems)
            is MediaPickerAction.AddSelectionToPlaylist -> addSelectionToPlaylist(action.playlistId)
            is MediaPickerAction.CreatePlaylistWithSelection -> createPlaylistWithSelection(action.name)
            is MediaPickerAction.DismissAddToPlaylist -> dismissAddToPlaylist()
        }
    }

    private fun startMediaCollection() {
        mediaSynchronizer.startSync()
        collectMedia()
    }

    private fun collectMedia() {
        mediaCollectJob?.cancel()
        stateInternal.update { currentState ->
            currentState.copy(mediaDataState = DataState.Loading)
        }
        mediaCollectJob = viewModelScope.launch {
            launch {
                getSortedMediaUseCase(folderPath).collect { media ->
                    stateInternal.update { it.copy(mediaDataState = DataState.Success(media)) }
                }
            }
            launch {
                getRecentlyPlayedVideoUseCase(folderPath).collect { recentlyPlayed ->
                    stateInternal.update { it.copy(recentlyPlayedVideo = recentlyPlayed) }
                }
            }
        }
    }

    private fun collectPreferences() {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect {
                stateInternal.update { currentState ->
                    currentState.copy(preferences = it)
                }
            }
        }
    }

    private fun collectPlaylists() {
        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { playlists ->
                stateInternal.update {
                    it.copy(
                        playlists = playlists.filter { playlist ->
                            playlist.type == PlaylistType.LOCAL
                        },
                    )
                }
            }
        }
    }

    private var pendingPlaylistVideos: List<Video> = emptyList()

    private fun showAddToPlaylist(selectedItems: Set<SelectionItem>) {
        stateInternal.update {
            it.copy(
                addToPlaylistState = AddToPlaylistState(
                    isVisible = true,
                    isSaving = true,
                ),
            )
        }
        viewModelScope.launch {
            try {
                val videos = selectedItems.toVideos()
                pendingPlaylistVideos = videos
                stateInternal.update {
                    it.copy(
                        addToPlaylistState = AddToPlaylistState(
                            isVisible = true,
                            hasVideos = videos.isNotEmpty(),
                            errorRes = if (videos.isEmpty()) {
                                R.string.playlist_selection_empty
                            } else {
                                null
                            },
                        ),
                    )
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Throwable) {
                stateInternal.update {
                    it.copy(
                        addToPlaylistState = AddToPlaylistState(
                            isVisible = true,
                            errorRes = R.string.playlist_selection_read_failed,
                        ),
                    )
                }
            }
        }
    }

    private fun addSelectionToPlaylist(playlistId: Long) {
        savePlaylistSelection {
            playlistRepository.addVideos(
                playlistId = playlistId,
                videoUris = pendingPlaylistVideos.map(Video::uriString),
            )
        }
    }

    private fun createPlaylistWithSelection(name: String) {
        savePlaylistSelection {
            playlistRepository.create(
                name = name,
                videoUris = pendingPlaylistVideos.map(Video::uriString),
            )
            pendingPlaylistVideos.size
        }
    }

    private fun savePlaylistSelection(block: suspend () -> Int) {
        if (pendingPlaylistVideos.isEmpty() || stateInternal.value.addToPlaylistState.isSaving) return
        stateInternal.update {
            it.copy(addToPlaylistState = it.addToPlaylistState.copy(isSaving = true, errorRes = null))
        }
        viewModelScope.launch {
            try {
                val addedCount = block()
                pendingPlaylistVideos = emptyList()
                stateInternal.update {
                    it.copy(addToPlaylistState = AddToPlaylistState())
                }
                showPlaylistItemsAddedToast(addedCount)
            } catch (error: kotlinx.coroutines.CancellationException) {
                stateInternal.update {
                    it.copy(addToPlaylistState = it.addToPlaylistState.copy(isSaving = false))
                }
                throw error
            } catch (_: Throwable) {
                stateInternal.update {
                    it.copy(
                        addToPlaylistState = it.addToPlaylistState.copy(
                            isSaving = false,
                            errorRes = R.string.playlist_update_failed,
                        ),
                    )
                }
            }
        }
    }

    private fun dismissAddToPlaylist() {
        if (stateInternal.value.addToPlaylistState.isSaving) return
        pendingPlaylistVideos = emptyList()
        stateInternal.update {
            it.copy(addToPlaylistState = AddToPlaylistState())
        }
    }

    private fun showPlaylistItemsAddedToast(addedCount: Int) {
        val message = if (addedCount == 0) {
            systemService.getString(R.string.already_in_playlist)
        } else {
            systemService.getQuantityString(
                R.plurals.added_videos_to_playlist,
                addedCount,
                addedCount,
            )
        }
        systemService.showToast(message, Toast.LENGTH_SHORT)
    }

    private fun playSelectedItems(selectedItems: Set<SelectionItem>) {
        viewModelScope.launch {
            val videoUris = selectedItems.toVideoUris()
            output.playVideos(videoUris)
        }
    }

    private fun deleteSelectedItems(selectedItems: Set<SelectionItem>, permanently: Boolean) {
        viewModelScope.launch {
            val videoUris = selectedItems.toVideoUris()
            mediaOperationsService.deleteMedia(videoUris, permanently = permanently)
        }
    }

    private fun shareSelectedItems(selectedItems: Set<SelectionItem>) {
        viewModelScope.launch {
            val videoUris = selectedItems.toVideoUris()
            mediaOperationsService.shareMedia(videoUris)
        }
    }

    private fun transferSelectedItems(selectedItems: Set<SelectionItem>, mode: TransferMode) {
        transferJob = viewModelScope.launch {
            val treeUri = systemService.pickFolder() ?: return@launch
            val videoUris = selectedItems.toVideoUris()
            if (videoUris.isEmpty()) return@launch

            stateInternal.update {
                it.copy(
                    transferFlow = TransferFlowState.Processing(
                        mode = mode,
                        progress = TransferProgress(totalFiles = videoUris.size),
                    ),
                )
            }

            mediaOperationsService.transferMedia(
                uris = videoUris,
                folderUri = treeUri,
                mode = mode,
            ).collect { event ->
                when (event) {
                    is TransferEvent.Progress -> stateInternal.update {
                        (it.transferFlow as? TransferFlowState.Processing)?.let { state ->
                            it.copy(transferFlow = state.copy(progress = event.progress))
                        } ?: it
                    }

                    is TransferEvent.Completed -> {
                        stateInternal.update { it.copy(transferFlow = TransferFlowState.Idle) }
                        showTransferCompleteToast(mode, event.result)
                    }
                }
            }
        }
    }

    private fun showTransferCompleteToast(mode: TransferMode, result: TransferResult) {
        val message = when {
            result.sameFolderSkipped > 0 && result.succeeded == 0 && result.failed == 0 ->
                systemService.getString(R.string.cannot_move_to_same_folder)

            result.failed > 0 -> systemService.getQuantityString(
                if (mode == TransferMode.MOVE) R.plurals.move_failed else R.plurals.copy_failed,
                result.failed,
                result.failed,
            )

            result.originalsNotDeleted -> systemService.getQuantityString(
                R.plurals.moved_videos_originals_remain,
                result.succeeded,
                result.succeeded,
            )

            mode == TransferMode.MOVE -> systemService.getQuantityString(
                R.plurals.moved_videos_result,
                result.succeeded,
                result.succeeded,
            )

            else -> systemService.getQuantityString(
                R.plurals.copied_videos_result,
                result.succeeded,
                result.succeeded,
            )
        }
        systemService.showToast(message, Toast.LENGTH_SHORT)
    }

    private fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        stateInternal.update { it.copy(transferFlow = TransferFlowState.Idle) }
    }

    private fun showMediaInfo(video: Video) {
        viewModelScope.launch {
            val mediaInfo = mediaRepository.getMediaInfo(video.uriString)
            if (mediaInfo != null) {
                stateInternal.update { it.copy(mediaInfo = mediaInfo) }
            }
        }
    }

    private fun renameVideo(uri: Uri, to: String) {
        viewModelScope.launch {
            mediaOperationsService.renameMedia(uri, to)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            stateInternal.update { it.copy(refreshing = true) }
            mediaSynchronizer.refresh()
            stateInternal.update { it.copy(refreshing = false) }
        }
    }

    private fun updateMenu(preferences: ApplicationPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { preferences }
        }
    }

    private fun requestHideSelectedItems(selectedItems: Set<SelectionItem>) {
        viewModelScope.launch {
            val videoItems = selectedItems.toVideos()
            if (videoItems.isEmpty()) return@launch
            val hasPin = vaultPinRepository.hasPinSet()
            when {
                !hasPin -> {
                    stateInternal.update { it.copy(hideFlow = HideFlowState.SetupPin(videoItems)) }
                }
                !vaultPinRepository.hasShownHideConfirmation() -> {
                    stateInternal.update { it.copy(hideFlow = HideFlowState.ConfirmHide(videoItems)) }
                }
                else -> {
                    hideVideoItems(videoItems)
                }
            }
        }
    }

    private fun setVaultPinAndHide(pin: String) {
        val pending = (stateInternal.value.hideFlow as? HideFlowState.SetupPin)?.items ?: return
        viewModelScope.launch {
            vaultPinRepository.setPin(pin)
            hideVideoItems(pending)
            vaultPinRepository.setHideConfirmationShown()
            stateInternal.update { it.copy(hideFlow = HideFlowState.BiometricSetup) }
        }
    }

    private fun completeBiometricSetup(enabled: Boolean) {
        if (stateInternal.value.hideFlow != HideFlowState.BiometricSetup) return
        viewModelScope.launch {
            vaultPinRepository.setBiometricEnabled(enabled)
            stateInternal.update { it.copy(hideFlow = HideFlowState.Idle) }
        }
    }

    private fun confirmHidePendingItems() {
        val pending = (stateInternal.value.hideFlow as? HideFlowState.ConfirmHide)?.items ?: return
        viewModelScope.launch {
            hideVideoItems(pending)
            vaultPinRepository.setHideConfirmationShown()
            stateInternal.update { it.copy(hideFlow = HideFlowState.Idle) }
        }
    }

    private suspend fun hideVideoItems(videos: List<Video>) {
        stateInternal.update { it.copy(hideFlow = HideFlowState.Processing) }
        vaultRepository.hideVideos(videos)
        stateInternal.update { it.copy(hideFlow = HideFlowState.Idle) }
    }

    private suspend fun Set<SelectionItem>.toVideos(): List<Video> {
        val preferences = stateInternal.value.preferences
        return flatMap { selectionItem ->
            when (selectionItem) {
                is SelectionItem.Video -> listOfNotNull(mediaRepository.getVideoByUri(selectionItem.uriString))
                is SelectionItem.Folder -> {
                    val videos = getSortedVideosUseCase(selectionItem.path).first()
                    val filteredVideos = if (preferences.mediaViewMode == MediaViewMode.FOLDERS) {
                        videos.filter { it.parentPath == selectionItem.path }
                    } else {
                        videos
                    }
                    filteredVideos
                }
            }
        }.distinctBy(Video::uriString)
    }

    private suspend fun Set<SelectionItem>.toVideoUris(): List<Uri> {
        return toVideos().map { it.uriString.toUri() }
    }
}

@Stable
data class MediaPickerUiState(
    val folderName: String?,
    val refreshing: Boolean = false,
    val recentlyPlayedVideo: Video? = null,
    val mediaDataState: DataState<MediaHolder?> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val mediaInfo: dev.anilbeesetti.nextplayer.core.model.MediaInfo? = null,
    val hideFlow: HideFlowState = HideFlowState.Idle,
    val transferFlow: TransferFlowState = TransferFlowState.Idle,
    val playlists: List<PlaylistSummary> = emptyList(),
    val addToPlaylistState: AddToPlaylistState = AddToPlaylistState(),
) {
    val recentlyPlayedFolder: Folder?
        get() = recentlyPlayedVideo?.let { video ->
            (mediaDataState as? DataState.Success)?.value?.folders?.findClosestFolder(video.path)
        }
}

@Stable
data class AddToPlaylistState(
    val isVisible: Boolean = false,
    val isSaving: Boolean = false,
    val hasVideos: Boolean = false,
    val errorRes: Int? = null,
)

sealed interface TransferFlowState {
    data object Idle : TransferFlowState
    data class Processing(val mode: TransferMode, val progress: TransferProgress) : TransferFlowState
}

sealed interface HideFlowState {
    data object Idle : HideFlowState
    data class ConfirmHide(val items: List<Video>) : HideFlowState
    data class SetupPin(val items: List<Video>) : HideFlowState
    data object BiometricSetup : HideFlowState

    data object Processing : HideFlowState
}

sealed interface MediaPickerAction {
    data object OnNavigateUpClick : MediaPickerAction
    data class OnPlayVideo(val uri: Uri) : MediaPickerAction
    data class OnFolderClick(val folderPath: String) : MediaPickerAction
    data object OnSettingsClick : MediaPickerAction
    data object OnSearchClick : MediaPickerAction
    data object OnVaultClick : MediaPickerAction
    data object Refresh : MediaPickerAction
    data class RenameVideo(val uri: Uri, val to: String) : MediaPickerAction
    data class UpdateMenu(val preferences: ApplicationPreferences) : MediaPickerAction
    data object OnPermissionAccepted : MediaPickerAction
    data class PlaySelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class DeleteSelectedItems(val selectionItems: Set<SelectionItem>, val permanently: Boolean = false) : MediaPickerAction
    data class ShareSelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class CopySelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class MoveSelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data object CancelTransfer : MediaPickerAction
    data class ShowMediaInfo(val video: Video) : MediaPickerAction
    data object DismissMediaInfo : MediaPickerAction
    data class RequestHideSelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class SetVaultPinAndHide(val pin: String) : MediaPickerAction
    data class CompleteBiometricSetup(val enabled: Boolean) : MediaPickerAction
    data object ConfirmHidePendingItems : MediaPickerAction
    data object DismissHideFlow : MediaPickerAction
    data class ShowAddToPlaylist(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class AddSelectionToPlaylist(val playlistId: Long) : MediaPickerAction
    data class CreatePlaylistWithSelection(val name: String) : MediaPickerAction
    data object DismissAddToPlaylist : MediaPickerAction
}
