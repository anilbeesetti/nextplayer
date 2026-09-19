package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

const val VAULT_PIN_LENGTH = 4

@KoinViewModel
class VaultViewModel(
    private val vaultRepository: VaultRepository,
    private val vaultPinRepository: VaultPinRepository,
    private val getHiddenVideosUseCase: GetHiddenVideosUseCase,
    private val preferencesRepository: PreferencesRepository,
    @InjectedParam internal var output: Output,
) : MviViewModel<VaultUiState, VaultAction>() {

    data class Output(
        val navigateUp: () -> Unit,
        val playVideo: (Uri) -> Unit,
        val playVideos: (List<Uri>) -> Unit,
    )

    private val stateInternal = MutableStateFlow(VaultUiState())
    private val sortOverride = MutableStateFlow<Sort?>(null)
    private val vaultState = combine(
        stateInternal,
        sortOverride,
        preferencesRepository.applicationPreferences,
    ) { state, sort, preferences ->
        state.copy(
            preferences = preferences,
            sort = sort ?: Sort(by = preferences.sortBy, order = preferences.sortOrder),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val hiddenVideos = vaultState
        .map { if (it.stage == VaultStage.UNLOCKED) it.sort else null }
        .distinctUntilChanged()
        .flatMapLatest { sort ->
            if (sort == null) {
                flowOf(emptyList())
            } else {
                getHiddenVideosUseCase(sort)
                    .map<List<Video>, List<Video>?> { it }
                    .onStart { emit(null) }
            }
        }

    override val state: StateFlow<VaultUiState> = combine(vaultState, hiddenVideos) { state, videos ->
        state.copy(
            hiddenVideos = if (state.stage == VaultStage.UNLOCKED) videos.orEmpty() else emptyList(),
            isLoading = state.stage == VaultStage.UNLOCKED && videos == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000L),
        initialValue = stateInternal.value,
    )

    private val eventsInternal = Channel<VaultEvent>()
    val events = eventsInternal.receiveAsFlow()

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
        sortOverride.value = sort
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
            stateInternal.update { it.copy(biometricEnabled = enabled) }
            unlockVault()
        }
    }

    private fun setBiometricEnabled(enabled: Boolean) {
        if (stateInternal.value.stage != VaultStage.UNLOCKED) return
        viewModelScope.launch {
            vaultPinRepository.setBiometricEnabled(enabled)
            stateInternal.update { it.copy(biometricEnabled = enabled) }
        }
    }

    private fun unlockVault() {
        stateInternal.update { it.copy(stage = VaultStage.UNLOCKED, pinErrorCount = 0) }
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
        return state.value.hiddenVideos.filter { it.uriString in selectedUris }
    }
}

enum class VaultStage {
    LOADING,
    LOCKED,
    SET_PIN,
    CONFIRM_PIN,
    BIOMETRIC_SETUP,
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
