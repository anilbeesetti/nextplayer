package dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
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
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.findClosestFolder
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.FolderArgs
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@HiltViewModel(assistedFactory = MediaPickerViewModel.Factory::class)
class MediaPickerViewModel @AssistedInject constructor(
    @Assisted private val folderArgs: FolderArgs,
    private val getSortedMediaUseCase: GetSortedMediaUseCase,
    private val getRecentlyPlayedVideoUseCase: GetRecentlyPlayedVideoUseCase,
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val mediaOperationsService: MediaOperationsService,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaSynchronizer: MediaSynchronizer,
    private val vaultRepository: VaultRepository,
    private val vaultPinRepository: VaultPinRepository,
    private val systemService: SystemService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(folderArgs: FolderArgs): MediaPickerViewModel
    }

    val folderPath = folderArgs.folderId

    private val uiStateInternal = MutableStateFlow(
        MediaPickerUiState(
            folderName = folderPath?.let { File(folderPath).prettyName },
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    private val eventsInternal = Channel<MediaPickerEvent>()
    val events = eventsInternal.receiveAsFlow()

    private var mediaCollectJob: Job? = null
    private var transferJob: Job? = null

    init {
        if (ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED) {
            startMediaCollection()
        }
        collectPreferences()
    }

    fun onAction(action: MediaPickerAction) {
        when (action) {
            is MediaPickerAction.Refresh -> refresh()
            is MediaPickerAction.RenameVideo -> renameVideo(action.uri, action.to)
            is MediaPickerAction.UpdateMenu -> updateMenu(action.preferences)
            is MediaPickerAction.OnPermissionAccepted -> startMediaCollection()
            is MediaPickerAction.PlaySelectedItems -> playSelectedItems(action.selectionItems)
            is MediaPickerAction.DeleteSelectedItems -> deleteSelectedItems(action.selectionItems)
            is MediaPickerAction.ShareSelectedItems -> shareSelectedItems(action.selectionItems)
            is MediaPickerAction.ShowMediaInfo -> showMediaInfo(action.video)
            MediaPickerAction.DismissMediaInfo -> uiStateInternal.update { it.copy(mediaInfo = null) }
            is MediaPickerAction.CopySelectedItems -> transferSelectedItems(action.selectionItems, TransferMode.COPY)
            is MediaPickerAction.MoveSelectedItems -> transferSelectedItems(action.selectionItems, TransferMode.MOVE)
            MediaPickerAction.CancelTransfer -> cancelTransfer()
            is MediaPickerAction.RequestHideSelectedItems -> requestHideSelectedItems(action.selectionItems)
            is MediaPickerAction.SetVaultPinAndHide -> setVaultPinAndHide(action.pin)
            MediaPickerAction.ConfirmHidePendingItems -> confirmHidePendingItems()
            MediaPickerAction.DismissHideFlow -> uiStateInternal.update { it.copy(hideFlow = HideFlowState.Idle) }
        }
    }

    private fun startMediaCollection() {
        mediaSynchronizer.startSync()
        collectMedia()
    }

    private fun collectMedia() {
        mediaCollectJob?.cancel()
        uiStateInternal.update { currentState ->
            currentState.copy(mediaDataState = DataState.Loading)
        }
        mediaCollectJob = viewModelScope.launch {
            combine(
                getSortedMediaUseCase.invoke(folderPath),
                getRecentlyPlayedVideoUseCase.invoke(folderPath),
            ) { media, recentlyPlayed ->
                media to recentlyPlayed
            }.collect { (media, recentlyPlayed) ->
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        mediaDataState = DataState.Success(media),
                        recentlyPlayedVideo = recentlyPlayed,
                        recentlyPlayedFolder = recentlyPlayed?.let { media?.folders?.findClosestFolder(it.path) }
                    )
                }
            }
        }
    }

    private fun collectPreferences() {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect {
                uiStateInternal.update { currentState ->
                    currentState.copy(preferences = it)
                }
            }
        }
    }

    private fun playSelectedItems(selectedItems: Set<SelectionItem>) {
        viewModelScope.launch {
            val videoUris = selectedItems.toVideoUris()
            eventsInternal.send(MediaPickerEvent.PlayVideos(videoUris))
        }
    }

    private fun deleteSelectedItems(selectedItems: Set<SelectionItem>) {
        viewModelScope.launch {
            val videoUris = selectedItems.toVideoUris()
            mediaOperationsService.deleteMedia(videoUris)
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

            uiStateInternal.update {
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
                    is TransferEvent.Progress -> uiStateInternal.update {
                        (it.transferFlow as? TransferFlowState.Processing)?.let { state ->
                            it.copy(transferFlow = state.copy(progress = event.progress))
                        } ?: it
                    }

                    is TransferEvent.Completed -> {
                        uiStateInternal.update { it.copy(transferFlow = TransferFlowState.Idle) }
                        eventsInternal.send(MediaPickerEvent.TransferComplete(mode, event.result))
                    }
                }
            }
        }
    }

    private fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        uiStateInternal.update { it.copy(transferFlow = TransferFlowState.Idle) }
    }

    private fun showMediaInfo(video: Video) {
        viewModelScope.launch {
            val mediaInfo = mediaRepository.getMediaInfo(video.uriString)
            if (mediaInfo != null) {
                uiStateInternal.update { it.copy(mediaInfo = mediaInfo) }
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
            uiStateInternal.update { it.copy(refreshing = true) }
            mediaSynchronizer.refresh()
            uiStateInternal.update { it.copy(refreshing = false) }
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
                    uiStateInternal.update { it.copy(hideFlow = HideFlowState.SetupPin(videoItems)) }
                }
                !vaultPinRepository.hasShownHideConfirmation() -> {
                    uiStateInternal.update { it.copy(hideFlow = HideFlowState.ConfirmHide(videoItems)) }
                }
                else -> {
                    hideVideoItems(videoItems)
                }
            }
        }
    }

    private fun setVaultPinAndHide(pin: String) {
        val pending = (uiStateInternal.value.hideFlow as? HideFlowState.SetupPin)?.items ?: return
        viewModelScope.launch {
            vaultPinRepository.setPin(pin)
            hideVideoItems(pending)
            vaultPinRepository.setHideConfirmationShown()
            uiStateInternal.update { it.copy(hideFlow = HideFlowState.HowToFindInfo) }
        }
    }

    private fun confirmHidePendingItems() {
        val pending = (uiStateInternal.value.hideFlow as? HideFlowState.ConfirmHide)?.items ?: return
        viewModelScope.launch {
            hideVideoItems(pending)
            vaultPinRepository.setHideConfirmationShown()
            uiStateInternal.update { it.copy(hideFlow = HideFlowState.Idle) }
        }
    }

    private suspend fun hideVideoItems(videos: List<Video>) {
        uiStateInternal.update { it.copy(hideFlow = HideFlowState.Processing) }
        vaultRepository.hideVideos(videos)
        uiStateInternal.update { it.copy(hideFlow = HideFlowState.Idle) }
    }

    private suspend fun Set<SelectionItem>.toVideos(): List<Video> {
        val preferences = uiStateInternal.value.preferences
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
        }
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
    val recentlyPlayedFolder: Folder? = null,
    val mediaDataState: DataState<MediaHolder?> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val mediaInfo: dev.anilbeesetti.nextplayer.core.model.MediaInfo? = null,
    val hideFlow: HideFlowState = HideFlowState.Idle,
    val transferFlow: TransferFlowState = TransferFlowState.Idle,
)

sealed interface TransferFlowState {
    data object Idle : TransferFlowState
    data class Processing(val mode: TransferMode, val progress: TransferProgress) : TransferFlowState
}

sealed interface HideFlowState {
    data object Idle : HideFlowState
    data class ConfirmHide(val items: List<Video>) : HideFlowState
    data class SetupPin(val items: List<Video>) : HideFlowState
    data object HowToFindInfo : HideFlowState

    data object Processing : HideFlowState
}

sealed interface MediaPickerAction {
    data object Refresh : MediaPickerAction
    data class RenameVideo(val uri: Uri, val to: String) : MediaPickerAction
    data class UpdateMenu(val preferences: ApplicationPreferences) : MediaPickerAction
    data object OnPermissionAccepted : MediaPickerAction
    data class PlaySelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class DeleteSelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class ShareSelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class CopySelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class MoveSelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data object CancelTransfer : MediaPickerAction
    data class ShowMediaInfo(val video: Video): MediaPickerAction
    data object DismissMediaInfo : MediaPickerAction
    data class RequestHideSelectedItems(val selectionItems: Set<SelectionItem>) : MediaPickerAction
    data class SetVaultPinAndHide(val pin: String) : MediaPickerAction
    data object ConfirmHidePendingItems : MediaPickerAction
    data object DismissHideFlow : MediaPickerAction
}

sealed interface MediaPickerEvent {
    data class PlayVideos(val uris: List<Uri>) : MediaPickerEvent
    data class TransferComplete(
        val mode: TransferMode,
        val result: TransferResult,
    ) : MediaPickerEvent
}
