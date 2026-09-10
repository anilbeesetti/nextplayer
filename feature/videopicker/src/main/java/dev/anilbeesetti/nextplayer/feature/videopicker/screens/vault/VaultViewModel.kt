package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultPinRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import dev.anilbeesetti.nextplayer.core.domain.GetHiddenVideosUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.MediaInfo
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val VAULT_PIN_LENGTH = 4

@HiltViewModel(assistedFactory = VaultViewModel.Factory::class)
class VaultViewModel @AssistedInject constructor(
    private val vaultRepository: VaultRepository,
    private val vaultPinRepository: VaultPinRepository,
    private val getHiddenVideosUseCase: GetHiddenVideosUseCase,
    private val preferencesRepository: PreferencesRepository,
    @Assisted internal var output: Output,
) : MviViewModel<VaultUiState, VaultAction>() {

    data class Output(
        val navigateUp: () -> Unit,
        val playVideo: (Uri) -> Unit,
        val playVideos: (List<Uri>) -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(output: Output): VaultViewModel
    }

    private val stateInternal = MutableStateFlow(VaultUiState())
    override val state: StateFlow<VaultUiState> = stateInternal.asStateFlow()

    private val eventsInternal = Channel<VaultEvent>()
    val events = eventsInternal.receiveAsFlow()

    private var hiddenVideosJob: Job? = null
    private var hasVaultSortOverride = false

    init {
        viewModelScope.launch {
            val hasPin = vaultPinRepository.hasPinSet()
            val biometricEnabled = hasPin && vaultPinRepository.isBiometricEnabled()
            stateInternal.update {
                it.copy(
                    stage = if (hasPin) VaultStage.LOCKED else VaultStage.SET_PIN,
                    biometricEnabled = biometricEnabled,
                )
            }
        }
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                val inheritedSort = Sort(by = prefs.sortBy, order = prefs.sortOrder)
                val shouldRefresh = !hasVaultSortOverride &&
                    stateInternal.value.stage == VaultStage.UNLOCKED &&
                    stateInternal.value.sort != inheritedSort
                stateInternal.update {
                    it.copy(
                        preferences = prefs,
                        sort = if (hasVaultSortOverride) it.sort else inheritedSort,
                    )
                }
                if (shouldRefresh) collectHiddenVideos()
            }
        }
    }

    override fun onAction(action: VaultAction) {
        when (action) {
            is VaultAction.NavigateUp -> output.navigateUp()

            is VaultAction.SubmitNewPin -> submitNewPin(action.pin)
            is VaultAction.SubmitPinConfirmation -> submitPinConfirmation(action.pin)
            is VaultAction.SubmitUnlockPin -> submitUnlockPin(action.pin)
            is VaultAction.BiometricAuthenticated -> {
                if (stateInternal.value.stage == VaultStage.LOCKED && stateInternal.value.biometricEnabled) unlockVault()
            }
            is VaultAction.CompleteBiometricSetup -> completeBiometricSetup(action.enabled)
            is VaultAction.SetBiometricEnabled -> setBiometricEnabled(action.enabled)
            is VaultAction.DismissHowToFindInfo -> dismissHowToFindInfo()
            is VaultAction.PlayVideo -> playVideo(action.video)
            is VaultAction.PlaySelected -> playSelected(action.selectionItems)
            is VaultAction.UnhideSelected -> unhideVideos(action.selectionItems)
            is VaultAction.DeleteSelected -> deleteVideos(action.selectionItems)
            is VaultAction.ShowMediaInfo -> showMediaInfo(action.video)
            is VaultAction.DismissMediaInfo -> stateInternal.update { it.copy(mediaInfo = null) }
            is VaultAction.UpdateSort -> updateSort(action.sort)
        }
    }

    private fun updateSort(sort: Sort) {
        hasVaultSortOverride = true
        val shouldRefresh = stateInternal.value.stage == VaultStage.UNLOCKED &&
            stateInternal.value.sort != sort
        stateInternal.update { it.copy(sort = sort) }
        if (shouldRefresh) collectHiddenVideos()
    }

    private fun submitNewPin(pin: String) {
        stateInternal.update { it.copy(pendingPin = pin, stage = VaultStage.CONFIRM_PIN, pinErrorCount = 0) }
    }

    private fun submitPinConfirmation(pin: String) {
        val pendingPin = stateInternal.value.pendingPin
        if (pin != pendingPin) {
            // PINs don't match — go back to SET_PIN so the user starts over from scratch
            stateInternal.update {
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
            stateInternal.update {
                it.copy(
                    stage = VaultStage.BIOMETRIC_SETUP,
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
                unlockVault()
            } else {
                stateInternal.update { it.copy(pinErrorCount = it.pinErrorCount + 1) }
            }
        }
    }

    private fun completeBiometricSetup(enabled: Boolean) {
        if (stateInternal.value.stage != VaultStage.BIOMETRIC_SETUP) return
        viewModelScope.launch {
            vaultPinRepository.setBiometricEnabled(enabled)
            stateInternal.update { it.copy(stage = VaultStage.HOW_TO_FIND_INFO, biometricEnabled = enabled) }
        }
    }

    private fun setBiometricEnabled(enabled: Boolean) {
        if (stateInternal.value.stage != VaultStage.UNLOCKED) return
        viewModelScope.launch {
            vaultPinRepository.setBiometricEnabled(enabled)
            stateInternal.update { it.copy(biometricEnabled = enabled) }
        }
    }

    private fun dismissHowToFindInfo() {
        unlockVault()
    }

    private fun unlockVault() {
        stateInternal.update { it.copy(stage = VaultStage.UNLOCKED, pinErrorCount = 0) }
        collectHiddenVideos()
    }

    private fun collectHiddenVideos() {
        hiddenVideosJob?.cancel()
        hiddenVideosJob = viewModelScope.launch {
            stateInternal.update { it.copy(isLoading = true) }
            getHiddenVideosUseCase(stateInternal.value.sort).collect { videos ->
                stateInternal.update { it.copy(hiddenVideos = videos, isLoading = false) }
            }
        }
    }

    private fun playVideo(video: Video) {
        output.playVideo(video.uriString.toUri())
    }

    private fun playSelected(selectionItems: Set<SelectionItem>) {
        val uris = selectionItems.toVideos().map { it.uriString.toUri() }
        if (uris.isNotEmpty()) {
            output.playVideos(uris)
        }
    }

    private fun unhideVideos(selectionItems: Set<SelectionItem>) {
        viewModelScope.launch {
            stateInternal.update { it.copy(isUnhiding = true) }
            val result = vaultRepository.unhideVideos(selectionItems.toVideos())
            stateInternal.update { it.copy(isUnhiding = false) }
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
                stateInternal.update { it.copy(mediaInfo = mediaInfo) }
            }
        }
    }

    private fun Set<SelectionItem>.toVideos(): List<Video> {
        val selectedUris = filterIsInstance<SelectionItem.Video>().map { it.uriString }.toSet()
        return stateInternal.value.hiddenVideos.filter { it.uriString in selectedUris }
    }
}

enum class VaultStage {
    LOADING,
    LOCKED,
    SET_PIN,
    CONFIRM_PIN,
    BIOMETRIC_SETUP,
    HOW_TO_FIND_INFO,
    UNLOCKED,
}

@Stable
data class VaultUiState(
    val stage: VaultStage = VaultStage.LOADING,
    val pendingPin: String? = null,
    val pinErrorCount: Int = 0,
    val setPinGeneration: Int = 0,
    val biometricEnabled: Boolean = false,
    val hiddenVideos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val isUnhiding: Boolean = false,
    val sort: Sort = Sort(by = Sort.By.DATE, order = Sort.Order.DESCENDING),
    val mediaInfo: MediaInfo? = null,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface VaultAction {
    data object NavigateUp : VaultAction

    data class SubmitNewPin(val pin: String) : VaultAction
    data class SubmitPinConfirmation(val pin: String) : VaultAction
    data class SubmitUnlockPin(val pin: String) : VaultAction
    data object BiometricAuthenticated : VaultAction
    data class CompleteBiometricSetup(val enabled: Boolean) : VaultAction
    data class SetBiometricEnabled(val enabled: Boolean) : VaultAction
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
    data class VideosRelocated(val count: Int) : VaultEvent
}
