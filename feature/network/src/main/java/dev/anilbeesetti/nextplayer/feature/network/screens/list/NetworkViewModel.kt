package dev.anilbeesetti.nextplayer.feature.network.screens.list

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NetworkUiState(
    val connections: List<NetworkConnection> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val repository: NetworkConnectionRepository,
    private val sshKeyStore: SshKeyStore,
) : MviViewModel<NetworkUiState, NetworkAction>() {

    override val state: StateFlow<NetworkUiState> = repository.getConnections()
        .map { NetworkUiState(connections = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkUiState(),
        )

    override fun onAction(action: NetworkAction) {
        when (action) {
            is NetworkAction.DeleteConnection -> deleteConnection(action.id)
        }
    }

    private fun deleteConnection(id: Long) {
        viewModelScope.launch {
            try {
                deleteConnectionAndCleanup(id, repository, sshKeyStore)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // There is no deletion error UI yet; key cleanup restores the row before failure.
            }
        }
    }
}

sealed interface NetworkAction {
    data class DeleteConnection(val id: Long) : NetworkAction
}

internal suspend fun deleteConnectionAndCleanup(
    id: Long,
    repository: NetworkConnectionRepository,
    sshKeyStore: SshKeyStore,
) {
    val connection = repository.getConnection(id) ?: return
    withContext(NonCancellable) {
        repository.delete(id)
        if (
            connection.authentication == NetworkAuthentication.SSH_KEY &&
            connection.privateKeyFileName.isNotBlank()
        ) {
            try {
                sshKeyStore.delete(connection.privateKeyFileName)
            } catch (keyFailure: Throwable) {
                try {
                    repository.upsert(connection)
                } catch (rollbackFailure: Throwable) {
                    if (rollbackFailure !== keyFailure) {
                        keyFailure.addSuppressed(rollbackFailure)
                    }
                }
                throw keyFailure
            }
        }
    }
}
