package dev.anilbeesetti.nextplayer.feature.network.screens.list

import android.net.Uri
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.extensions.collectWhileSubscribed
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NetworkUiState(
    val connections: List<NetworkConnection> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel(assistedFactory = NetworkViewModel.Factory::class)
class NetworkViewModel @AssistedInject constructor(
    private val repository: NetworkConnectionRepository,
    private val sshKeyStore: SshKeyStore,
    @Assisted internal var output: Output,
) : MviViewModel<NetworkUiState, NetworkAction>() {

    data class Output(
        val addConnection: () -> Unit,
        val editConnection: (Long) -> Unit,
        val openConnection: (Long) -> Unit,
        val openSettings: () -> Unit,
        val openStream: (Uri) -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(output: Output): NetworkViewModel
    }

    private val stateInternal = MutableStateFlow(NetworkUiState())
    override val state: StateFlow<NetworkUiState> = stateInternal.asStateFlow()

    init {
        repository.getConnections().collectWhileSubscribed(viewModelScope, stateInternal) { connections ->
            stateInternal.update { it.copy(connections = connections, isLoading = false) }
        }
    }

    override fun onAction(action: NetworkAction) {
        when (action) {
            is NetworkAction.AddConnection -> output.addConnection()
            is NetworkAction.EditConnection -> output.editConnection(action.id)
            is NetworkAction.OpenConnection -> output.openConnection(action.id)
            is NetworkAction.OpenSettings -> output.openSettings()
            is NetworkAction.OpenStream -> output.openStream(action.uri)

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
    data object AddConnection : NetworkAction
    data class EditConnection(val id: Long) : NetworkAction
    data class OpenConnection(val id: Long) : NetworkAction
    data object OpenSettings : NetworkAction
    data class OpenStream(val uri: Uri) : NetworkAction

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
