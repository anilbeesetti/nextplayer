package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            vaultRepository.vaultPreferences.collect { prefs ->
                _uiState.update { it.copy(hasPinSet = prefs.hasPinSet) }
            }
        }
    }

    fun onVaultUnlocked() {
        _uiState.update { it.copy(isUnlocked = true) }
        refreshVaultFiles()
    }

    fun refreshVaultFiles() {
        val files = mediaService.listVaultFiles()
        _uiState.update { it.copy(vaultFiles = files) }
    }

    fun setPin(rawPin: String) {
        viewModelScope.launch {
            val hashed = sha256(rawPin)
            vaultRepository.setPin(hashed)
            _uiState.update { it.copy(hasPinSet = true, pinError = false) }
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            vaultRepository.setPin(null)
            _uiState.update { it.copy(hasPinSet = false) }
        }
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
    val isUnhiding: Boolean = false,
    val pinError: Boolean = false,
)
