package dev.anilbeesetti.nextplayer.feature.network.screens.browse

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.isNetworkVideoFile
import dev.anilbeesetti.nextplayer.core.media.network.proxy.NetworkStreamingProxy
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class NetworkBrowseUiState(
    val title: String = "",
    val files: List<NetworkFile> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Browses a single folder on a network connection. Each folder is its own navigation destination
 * (like the media picker), so back navigation returns to the already-loaded parent instantly.
 */
@HiltViewModel(assistedFactory = NetworkBrowseViewModel.Factory::class)
class NetworkBrowseViewModel @AssistedInject constructor(
    /** The connection being browsed; exposed so the screen can build child-folder routes. */
    @Assisted val connectionId: Long,
    /** The folder path to list; `null` means the connection's root. */
    @Assisted private val path: String?,
    private val repository: NetworkConnectionRepository,
    private val streamingProxy: NetworkStreamingProxy,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(connectionId: Long, path: String?): NetworkBrowseViewModel
    }

    private var connection: NetworkConnection? = null
    private var client: NetworkClient? = null
    private var currentPath: String? = path

    private val _uiState = MutableStateFlow(NetworkBrowseUiState())
    val uiState: StateFlow<NetworkBrowseUiState> = _uiState.asStateFlow()

    private val _playEvents = Channel<Uri>()
    val playEvents = _playEvents.receiveAsFlow()

    init {
        connectAndLoad()
    }

    /** Loads the connection, (re)establishes the client, then lists the current folder. */
    private fun connectAndLoad() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val conn = connection ?: repository.getConnection(connectionId)?.also { connection = it }
            if (conn == null) {
                _uiState.value = NetworkBrowseUiState(isLoading = false, error = "Connection not found")
                return@launch
            }
            val activeClient = client ?: NetworkClientFactory.create(conn).also { client = it }
            if (!activeClient.isConnected()) {
                val connected = activeClient.connect()
                if (connected.isFailure) {
                    _uiState.value = NetworkBrowseUiState(
                        title = title(conn),
                        isLoading = false,
                        error = connected.exceptionOrNull()?.message,
                    )
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
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            client.listFiles(path).fold(
                onSuccess = { files ->
                    val visible = files
                        .filter { it.isDirectory || isNetworkVideoFile(it.name) }
                        .sortedWith(compareByDescending<NetworkFile> { it.isDirectory }.thenBy { it.name.lowercase() })
                    _uiState.value = NetworkBrowseUiState(
                        title = title(conn),
                        files = visible,
                        isLoading = false,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                },
            )
        }
    }

    /** Root folder shows the connection name; nested folders show the last path segment. */
    private fun title(conn: NetworkConnection): String =
        path?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: conn.name

    fun retry() {
        if (currentPath == null || client?.isConnected() != true) connectAndLoad() else loadCurrent()
    }

    fun playVideo(file: NetworkFile) {
        val conn = connection ?: return
        if (file.isDirectory) return
        viewModelScope.launch {
            val url = streamingProxy.registerStream(conn, file.path, file.name)
            _playEvents.send(url.toUri())
        }
    }

    override fun onCleared() {
        val client = client ?: return
        // Best-effort disconnect on a detached IO scope, since viewModelScope is already cancelled.
        CoroutineScope(Dispatchers.IO).launch { runCatching { client.disconnect() } }
    }
}
