package dev.anilbeesetti.nextplayer.feature.network.screens.browse

import android.net.Uri
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkUri
import dev.anilbeesetti.nextplayer.core.media.network.isNetworkVideoFile
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyMismatch
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkBrowseUiState(
    val title: String = "",
    val files: List<NetworkFile> = emptyList(),
    val isLoading: Boolean = true,
    val error: NetworkBrowseError? = null,
)

data class NetworkBrowseHostKeyMismatch(
    val trustedFingerprint: String,
    val presentedFingerprint: String,
)

data class NetworkBrowseError(
    val message: String?,
    val hostKeyMismatch: NetworkBrowseHostKeyMismatch? = null,
)

/**
 * Browses a single folder on a network connection. Each folder is its own navigation destination
 * (like the media picker), so back navigation returns to the already-loaded parent instantly.
 */
@HiltViewModel(assistedFactory = NetworkBrowseViewModel.Factory::class)
class NetworkBrowseViewModel @AssistedInject constructor(
    @Assisted private val input: Input,
    @Assisted internal var output: Output,
    private val repository: NetworkConnectionRepository,
    private val clientFactory: NetworkClientFactory,
) : MviViewModel<NetworkBrowseUiState, NetworkBrowseAction>() {

    data class Input(val connectionId: Long, val path: String?)
    data class Output(
        val navigateUp: () -> Unit,
        val playVideo: (Uri) -> Unit,
        val openFolder: (Long, String) -> Unit,
    )

    private val connectionId = input.connectionId
    private val path = input.path

    @AssistedFactory
    interface Factory {
        fun create(input: Input, output: Output): NetworkBrowseViewModel
    }

    private var connection: NetworkConnection? = null
    private var client: NetworkClient? = null
    private var currentPath: String? = path

    private val stateInternal = MutableStateFlow(NetworkBrowseUiState())
    override val state: StateFlow<NetworkBrowseUiState> = stateInternal.asStateFlow()

    init {
        connectAndLoad()
    }

    /** Loads the connection, (re)establishes the client, then lists the current folder. */
    private fun connectAndLoad() {
        stateInternal.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val conn = connection ?: repository.getConnection(connectionId)?.also { connection = it }
            if (conn == null) {
                stateInternal.update {
                    NetworkBrowseUiState(
                        isLoading = false,
                        error = NetworkBrowseError("Connection not found"),
                    )
                }
                return@launch
            }
            val activeClient = client ?: clientFactory.create(conn).also { client = it }
            if (!activeClient.isConnected()) {
                val connected = activeClient.connect()
                if (connected.isFailure) {
                    stateInternal.update {
                        NetworkBrowseUiState(
                            title = title(conn),
                            isLoading = false,
                            error = connected.exceptionOrNull()?.toNetworkBrowseError(),
                        )
                    }
                    return@launch
                }
            }
            if (currentPath == null) currentPath = activeClient.rootPath
            loadCurrent()
        }
    }

    private fun loadCurrent() {
        val client = client ?: return
        val conn = connection ?: return
        val path = currentPath ?: return
        stateInternal.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            client.listFiles(path).fold(
                onSuccess = { files ->
                    val visible = files
                        .filter { it.isDirectory || isNetworkVideoFile(it.name) }
                        .sortedWith(compareByDescending<NetworkFile> { it.isDirectory }.thenBy { it.name.lowercase() })
                    stateInternal.update {
                        NetworkBrowseUiState(
                            title = title(conn),
                            files = visible,
                            isLoading = false,
                        )
                    }
                },
                onFailure = { e ->
                    stateInternal.update { it.copy(isLoading = false, error = e.toNetworkBrowseError()) }
                },
            )
        }
    }

    /** Root folder shows the connection name; nested folders show the last path segment. */
    private fun title(conn: NetworkConnection): String =
        path?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: conn.name

    override fun onAction(action: NetworkBrowseAction) {
        when (action) {
            is NetworkBrowseAction.NavigateUp -> output.navigateUp()
            is NetworkBrowseAction.OpenFolder -> output.openFolder(connectionId, action.file.path)

            is NetworkBrowseAction.Retry -> retry()
            is NetworkBrowseAction.PlayVideo -> playVideo(action.file)
        }
    }

    private fun retry() {
        if (currentPath == null || client?.isConnected() != true) connectAndLoad() else loadCurrent()
    }

    private fun playVideo(file: NetworkFile) {
        val conn = connection ?: return
        if (file.isDirectory) return
        output.playVideo(NetworkUri.build(conn, file.path))
    }

    override fun onCleared() {
        val client = client ?: return
        // Best-effort disconnect on a detached IO scope, since viewModelScope is already cancelled.
        CoroutineScope(Dispatchers.IO).launch { runCatching { client.disconnect() } }
    }
}

sealed interface NetworkBrowseAction {
    data object NavigateUp : NetworkBrowseAction
    data class OpenFolder(val file: NetworkFile) : NetworkBrowseAction

    data object Retry : NetworkBrowseAction
    data class PlayVideo(val file: NetworkFile) : NetworkBrowseAction
}

private fun Throwable.toNetworkBrowseError(): NetworkBrowseError {
    val mismatch = generateSequence(this) { it.cause }
        .filterIsInstance<HostKeyMismatch>()
        .firstOrNull()
    return NetworkBrowseError(
        message = message,
        hostKeyMismatch = mismatch?.let {
            NetworkBrowseHostKeyMismatch(
                trustedFingerprint = it.expectedFingerprint,
                presentedFingerprint = it.presentedFingerprint,
            )
        },
    )
}
