package dev.anilbeesetti.nextplayer.feature.network.screens.addconnection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface SaveState {
    data object Idle : SaveState
    data object Testing : SaveState
    data class Error(val message: String?) : SaveState
}

@HiltViewModel(assistedFactory = AddConnectionViewModel.Factory::class)
class AddConnectionViewModel @AssistedInject constructor(
    @Assisted private val connectionId: Long?,
    private val repository: NetworkConnectionRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(connectionId: Long?): AddConnectionViewModel
    }

    val isEdit: Boolean = connectionId != null

    private val _existingConnection = MutableStateFlow<NetworkConnection?>(null)
    val existingConnection: StateFlow<NetworkConnection?> = _existingConnection.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _savedEvents = Channel<Unit>()
    val savedEvents = _savedEvents.receiveAsFlow()

    init {
        if (connectionId != null) {
            viewModelScope.launch { _existingConnection.value = repository.getConnection(connectionId) }
        }
    }

    /** Tests [connection] by connecting, and persists it (with the existing id when editing) on success. */
    fun testAndSave(connection: NetworkConnection) {
        if (_saveState.value == SaveState.Testing) return
        _saveState.value = SaveState.Testing
        viewModelScope.launch {
            val toSave = connection.copy(id = connectionId ?: 0)
            val client = NetworkClientFactory.create(toSave)
            val result = runCatching { client.connect().getOrThrow() }
            runCatching { client.disconnect() }
            if (result.isSuccess) {
                repository.upsert(toSave)
                _savedEvents.send(Unit)
                _saveState.value = SaveState.Idle
            } else {
                _saveState.value = SaveState.Error(result.exceptionOrNull()?.message)
            }
        }
    }

    fun clearError() {
        if (_saveState.value is SaveState.Error) _saveState.value = SaveState.Idle
    }
}
