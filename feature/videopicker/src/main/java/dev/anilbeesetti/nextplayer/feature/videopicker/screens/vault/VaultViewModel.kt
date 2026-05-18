package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val mediaService: MediaService,
    private val mediaSynchronizer: MediaSynchronizer,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            vaultRepository.vaultPreferences.collect { vaultPrefs ->
                _uiState.update { it.copy(hasPinSet = vaultPrefs.hasPinSet) }
            }
        }
        // Show first-time tip if vault has never been unlocked before
        val hasSeenTip = prefs.getBoolean("has_seen_vault_tip", false)
        if (!hasSeenTip) {
            _uiState.update { it.copy(showFirstTimeTip = true) }
        }
    }

    fun dismissFirstTimeTip() {
        prefs.edit().putBoolean("has_seen_vault_tip", true).apply()
        _uiState.update { it.copy(showFirstTimeTip = false) }
    }

    fun onVaultUnlocked() {
        _uiState.update { it.copy(isUnlocked = true) }
        refreshVaultFiles()
    }

    fun refreshVaultFiles() {
        val files = mediaService.listVaultFiles()
        val durations = files.associate { filename ->
            filename to mediaService.getVaultFileDuration(filename)
        }
        _uiState.update { it.copy(vaultFiles = files, vaultFileDurations = durations) }
    }

    fun setPin(rawPin: String) {
        viewModelScope.launch {
            val hashed = sha256(rawPin)
            vaultRepository.setPin(hashed)
            _uiState.update { it.copy(hasPinSet = true, pinError = false) }
        }
    }

    // Called when user wants to remove PIN — shows the verify-old-pin dialog
    fun requestClearPin() {
        _uiState.update { it.copy(showVerifyPinToClear = true, clearPinError = false) }
    }

    fun dismissClearPin() {
        _uiState.update { it.copy(showVerifyPinToClear = false, clearPinError = false) }
    }

    fun clearPinAfterVerify(rawPin: String) {
        viewModelScope.launch {
            val hashed = sha256(rawPin)
            val correct = vaultRepository.verifyPin(hashed)
            if (correct) {
                vaultRepository.setPin(null)
                _uiState.update {
                    it.copy(hasPinSet = false, showVerifyPinToClear = false, clearPinError = false)
                }
            } else {
                _uiState.update { it.copy(clearPinError = true) }
            }
        }
    }

    fun clearClearPinError() {
        _uiState.update { it.copy(clearPinError = false) }
    }

    suspend fun verifyPin(rawPin: String): Boolean {
        val hashed = sha256(rawPin)
        return vaultRepository.verifyPin(hashed)
    }

    fun onPinError() {
        _uiState.update { it.copy(pinError = true) }
    }

    fun clearPinError() {
        _uiState.update { it.copy(pinError = false) }
    }

    fun unhideVideos(filenames: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUnhiding = true) }
            val success = mediaService.unhideVideos(filenames)
            if (success) {
                mediaSynchronizer.refresh()
                refreshVaultFiles()
            }
            _uiState.update { it.copy(isUnhiding = false) }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

data class VaultUiState(
    val hasPinSet: Boolean = false,
    val isUnlocked: Boolean = false,
    val vaultFiles: List<String> = emptyList(),
    val vaultFileDurations: Map<String, Long> = emptyMap(),
    val isUnhiding: Boolean = false,
    val pinError: Boolean = false,
    val showVerifyPinToClear: Boolean = false,
    val clearPinError: Boolean = false,
    val showFirstTimeTip: Boolean = false,
)
