package dev.anilbeesetti.nextplayer.feature.videopicker.screens.webdav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.model.WebDavServer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TestConnectionResult(
    val isSuccess: Boolean,
    val message: String
)

@HiltViewModel
class WebDavViewModel @Inject constructor(
    private val webDavRepository: dev.anilbeesetti.nextplayer.core.data.repository.WebDavRepository
) : ViewModel() {
    
    val servers: StateFlow<List<WebDavServer>> = webDavRepository.getAllServers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _testConnectionResult = MutableStateFlow<TestConnectionResult?>(null)
    val testConnectionResult: StateFlow<TestConnectionResult?> = _testConnectionResult.asStateFlow()
    
    val filteredServers = combine(servers, searchQuery) { serverList, query ->
        if (query.isBlank()) {
            serverList
        } else {
            serverList.filter { server ->
                server.name.contains(query, ignoreCase = true) ||
                server.url.contains(query, ignoreCase = true)
            }
        }
    }
    
    init {
        loadServers()
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun addServer(server: WebDavServer) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                webDavRepository.addServer(server)
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            try {
                webDavRepository.deleteServer(serverId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun testConnection(server: WebDavServer) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _testConnectionResult.value = null
                
                val result = webDavRepository.testConnection(server)
                result.onSuccess { isConnected ->
                    _testConnectionResult.value = TestConnectionResult(
                        isSuccess = isConnected,
                        message = if (isConnected) "Connection successful!" else "Connection failed"
                    )
                    if (isConnected) {
                        // Update server connection status
                        webDavRepository.updateConnectionStatus(
                            server.id,
                            isConnected = true,
                            lastConnected = System.currentTimeMillis()
                        )
                    }
                }.onFailure { exception ->
                    _testConnectionResult.value = TestConnectionResult(
                        isSuccess = false,
                        message = exception.message ?: "Connection test failed"
                    )
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearTestConnectionResult() {
        _testConnectionResult.value = null
    }
    
    private fun loadServers() {
        // No need to manually load servers since we're using StateFlow from repository
    }
}
