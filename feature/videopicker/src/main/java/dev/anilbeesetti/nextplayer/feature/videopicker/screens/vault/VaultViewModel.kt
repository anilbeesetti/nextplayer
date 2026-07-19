package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultPinRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import dev.anilbeesetti.nextplayer.core.domain.GetHiddenVideosUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val VAULT_PIN_LENGTH = 4

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val vaultPinRepository: VaultPinRepository,
    private val getHiddenVideosUseCase: GetHiddenVideosUseCase,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(VaultUiState())
    val uiState = uiStateInternal.asStateFlow()

    private val eventsInternal = Channel<VaultEvent>()
    val events = eventsInternal.receiveAsFlow()

    private var hiddenVideosJob: Job? = null

    init {
        viewModelScope.launch {
            val hasPin = vaultPinRepository.hasPinSet()
            uiStateInternal.update {
                it.copy(stage = if (hasPin) VaultStage.LOCKED else VaultStage.SET_PIN)
            }
        }
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                uiStateInternal.update { it.copy(preferences = prefs) }
            }
        }
    }

    fun onAction(action: VaultAction) {
        when (action) {
            is VaultAction.SubmitNewPin -> submitNewPin(action.pin)
            is VaultAction.SubmitPinConfirmation -> submitPinConfirmation(action.pin)
            is VaultAction.SubmitUnlockPin -> submitUnlockPin(action.pin)
            VaultAction.DismissHowToFindInfo -> dismissHowToFindInfo()
            is VaultAction.PlayVideo -> playVideo(action.video)
            is VaultAction.PlaySelected -> playSelected(action.selectionItems)
            is VaultAction.UnhideSelected -> unhideVideos(action.selectionItems)
            is VaultAction.DeleteSelected -> deleteVideos(action.selectionItems)
            is VaultAction.ShowMediaInfo -> showMediaInfo(action.video)
            VaultAction.DismissMediaInfo -> uiStateInternal.update { it.copy(mediaInfo = null) }
            is VaultAction.UpdateSort -> uiStateInternal.update { it.copy(sort = action.sort) }
        }
    }

    private fun submitNewPin(pin: String) {
        uiStateInternal.update { it.copy(pendingPin = pin, stage = VaultStage.CONFIRM_PIN, pinErrorCount = 0) }
    }

    private fun submitPinConfirmation(pin: String) {
        val pendingPin = uiStateInternal.value.pendingPin
        if (pin != pendingPin) {
            // PINs don't match — go back to SET_PIN so the user starts over from scratch
            uiStateInternal.update {
                it.copy(
                    stage = VaultStage.SET_PIN,
                    pendingPin = null,
                    pinErrorCount = 0,
                    setPinGeneration = it.setPinGeneration + 1,
                )
            }
            return
        }
        viewModelScope.launch {
            vaultPinRepository.setPin(pin)
            uiStateInternal.update {
                it.copy(
                    stage = VaultStage.HOW_TO_FIND_INFO,
                    pendingPin = null,
                    pinErrorCount = 0,
                )
            }
        }
    }

    private fun submitUnlockPin(pin: String) {
        viewModelScope.launch {
            val isValid = vaultPinRepository.verifyPin(pin)
            if (isValid) {
                uiStateInternal.update { it.copy(stage = VaultStage.UNLOCKED, pinErrorCount = 0) }
                collectHiddenVideos()
            } else {
                uiStateInternal.update { it.copy(pinErrorCount = it.pinErrorCount + 1) }
            }
        }
    }

    private fun dismissHowToFindInfo() {
        uiStateInternal.update { it.copy(stage = VaultStage.UNLOCKED) }
        collectHiddenVideos()
    }

    private fun collectHiddenVideos() {
        hiddenVideosJob?.cancel()
        hiddenVideosJob = viewModelScope.launch {
            uiStateInternal.update { it.copy(isLoading = true) }
            getHiddenVideosUseCase(uiStateInternal.value.sort).collect { videos ->
                uiStateInternal.update { it.copy(hiddenVideos = videos, isLoading = false) }
            }
        }
    }

    private fun playVideo(video: Video) {
        viewModelScope.launch {
            eventsInternal.send(VaultEvent.PlayVideo(video.uriString.toUri()))
        }
    }

    private fun playSelected(selectionItems: Set<SelectionItem>) {
        viewModelScope.launch {
            val uris = selectionItems.toVideos().map { it.uriString.toUri() }
            if (uris.isNotEmpty()) {
                eventsInternal.send(VaultEvent.PlayVideos(uris))
            }
        }
    }

    private fun unhideVideos(selectionItems: Set<SelectionItem>) {
        viewModelScope.launch {
            uiStateInternal.update { it.copy(isUnhiding = true) }
            val result = vaultRepository.unhideVideos(selectionItems.toVideos())
            uiStateInternal.update { it.copy(isUnhiding = false) }
            if (result.relocatedCount > 0) {
                eventsInternal.send(VaultEvent.VideosRelocated(result.relocatedCount))
            }
        }
    }

    private fun deleteVideos(selectionItems: Set<SelectionItem>) {
        viewModelScope.launch {
            vaultRepository.deleteHiddenVideos(selectionItems.toVideos())
        }
    }

    private fun showMediaInfo(video: Video) {
        viewModelScope.launch {
            val mediaInfo = vaultRepository.getHiddenVideoInfo(video.id)
            if (mediaInfo != null) {
                uiStateInternal.update { it.copy(mediaInfo = mediaInfo) }
            }
        }
    }

    private fun Set<SelectionItem>.toVideos(): List<Video> {
        val selectedUris = filterIsInstance<SelectionItem.Video>().map { it.uriString }.toSet()
        return uiStateInternal.value.hiddenVideos.filter { it.uriString in selectedUris }
    }
}

enum class VaultStage {
    LOCKED,
    SET_PIN,
    CONFIRM_PIN,
    HOW_TO_FIND_INFO,
    UNLOCKED,
}

@Stable
data class VaultUiState(
    val stage: VaultStage = VaultStage.LOCKED,
    val pendingPin: String? = null,
    val pinErrorCount: Int = 0,
    val setPinGeneration: Int = 0,
    val hiddenVideos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val isUnhiding: Boolean = false,
    val sort: Sort = Sort(by = Sort.By.DATE, order = Sort.Order.DESCENDING),
    val mediaInfo: MediaInfo? = null,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface VaultAction {
    data class SubmitNewPin(val pin: String) : VaultAction
    data class SubmitPinConfirmation(val pin: String) : VaultAction
    data class SubmitUnlockPin(val pin: String) : VaultAction
    data object DismissHowToFindInfo : VaultAction
    data class PlayVideo(val video: Video) : VaultAction
    data class PlaySelected(val selectionItems: Set<SelectionItem>) : VaultAction
    data class UnhideSelected(val selectionItems: Set<SelectionItem>) : VaultAction
    data class DeleteSelected(val selectionItems: Set<SelectionItem>) : VaultAction
    data class ShowMediaInfo(val video: Video) : VaultAction
    data object DismissMediaInfo : VaultAction
    data class UpdateSort(val sort: Sort) : VaultAction
}

sealed interface VaultEvent {
    data class PlayVideo(val uri: Uri) : VaultEvent
    data class PlayVideos(val uris: List<Uri>) : VaultEvent
    data class VideosRelocated(val count: Int) : VaultEvent
}
