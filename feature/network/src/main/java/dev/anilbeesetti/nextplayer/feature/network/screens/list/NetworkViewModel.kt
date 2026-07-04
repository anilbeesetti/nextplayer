package dev.anilbeesetti.nextplayer.feature.network.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NetworkUiState(
    val connections: List<NetworkConnection> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val repository: NetworkConnectionRepository,
) : ViewModel() {

    val uiState: StateFlow<NetworkUiState> = repository.getConnections()
        .map { NetworkUiState(connections = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkUiState(),
        )

    fun deleteConnection(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
