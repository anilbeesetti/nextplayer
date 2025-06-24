package dev.anilbeesetti.nextplayer.feature.videopicker.screens.webdav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.WebDavRepository
import dev.anilbeesetti.nextplayer.core.model.WebDavFile
import dev.anilbeesetti.nextplayer.core.model.WebDavServer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri

enum class SortType(val displayName: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Date")
}

@HiltViewModel
class WebDavBrowserViewModel @Inject constructor(
    private val webDavRepository: WebDavRepository,
) : ViewModel() {
    
    private val _server = MutableStateFlow<WebDavServer?>(null)
    val server: StateFlow<WebDavServer?> = _server.asStateFlow()
    
    private val _allFiles = MutableStateFlow<List<WebDavFile>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _sortType = MutableStateFlow(SortType.NAME)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()
    val files: StateFlow<List<WebDavFile>> = combine(
        _allFiles,
        _searchQuery,
        _sortType,
    ) { allFiles, query, sortType ->
        val filteredFiles = if (query.isBlank()) {
            allFiles
        } else {
            allFiles.filter { file ->
                file.name.contains(query, ignoreCase = true)
            }
        }
        
        when (sortType) {
            SortType.NAME -> filteredFiles.sortedBy { file ->
                if (file.isDirectory) "0${file.name}" else "1${file.name}"
            }
            SortType.SIZE -> filteredFiles.sortedWith { a, b ->
                when {
                    a.isDirectory && !b.isDirectory -> -1
                    !a.isDirectory && b.isDirectory -> 1
                    else -> a.size.compareTo(b.size)
                }
            }
            SortType.DATE -> filteredFiles.sortedWith { a, b ->
                when {
                    a.isDirectory && !b.isDirectory -> -1
                    !a.isDirectory && b.isDirectory -> 1
                    else -> a.lastModified.compareTo(b.lastModified)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()
    
    
    private val pathHistory = mutableListOf<String>()
    
    fun loadServer(serverId: String) {
        viewModelScope.launch {
            try {
                val serverInfo = webDavRepository.getServerById(serverId)
                _server.value = serverInfo
                if (serverInfo != null) {
                    // 从服务器URL中提取基础路径作为起始路径
                    val basePath = extractBasePath(serverInfo.url)
                    _currentPath.value = basePath
                    loadFiles(basePath)
                } else {
                    _error.value = "Server not found"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }
        }
    }
    
    fun navigateToPath(path: String) {
        pathHistory.add(_currentPath.value)
        _currentPath.value = path
        loadFiles(path)
    }
    
    fun navigateBack() {
        if (pathHistory.isNotEmpty()) {
            val previousPath = pathHistory.removeLastOrNull() ?: getBasePath()
            _currentPath.value = previousPath
            loadFiles(previousPath)
        }
    }
    
    fun canNavigateBack(): Boolean {
        return pathHistory.isNotEmpty()
    }
    
    /**
     * Get the base path for the current server
     */
    private fun getBasePath(): String {
        val server = _server.value ?: return "/"
        return extractBasePath(server.url)
    }
    
    fun refresh() {
        loadFiles(_currentPath.value)
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun updateSortType(sortType: SortType) {
        _sortType.value = sortType
    }
    
    fun getServerUrl(): String {
        return _server.value?.url ?: ""
    }
    
    fun createAuthenticatedUri(filePath: String): android.net.Uri {
        val server = _server.value ?: return android.net.Uri.EMPTY
        
        // Create URL without embedded credentials (ExoPlayer doesn't handle them well)
        val baseUrl = server.url.trimEnd('/')
        val fullPath = if (filePath.startsWith("/")) filePath else "/$filePath"
        
        // Debug logging
        android.util.Log.d("WebDavBrowserVM", "Creating URI for file: $filePath")
        android.util.Log.d("WebDavBrowserVM", "Base URL: $baseUrl")
        android.util.Log.d("WebDavBrowserVM", "Full path: $fullPath")
        
        // Check if the path already contains the base URL's path to avoid duplication
        val finalUri = try {
            val baseUrlObj = java.net.URL(baseUrl)
            val baseUrlPath = baseUrlObj.path.trimEnd('/')
            
            if (baseUrlPath.isNotEmpty() && fullPath.startsWith(baseUrlPath)) {
                // Path already includes the base path, construct URL differently
                val baseUrlWithoutPath = "${baseUrlObj.protocol}://${baseUrlObj.authority}"
                // Manually encode only necessary characters (spaces, etc.) but keep brackets and other safe chars
                val encodedPath = encodePathForWebDav(fullPath)
                (baseUrlWithoutPath + encodedPath).toUri()
            } else {
                // Normal case: manually encode only necessary characters
                val encodedPath = encodePathForWebDav(fullPath)
                (baseUrl.trimEnd('/') + encodedPath).toUri()
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavBrowserVM", "Error parsing URL: $baseUrl", e)
            // Fallback to simple concatenation
            (baseUrl.trimEnd('/') + fullPath).toUri()
        }
        
        android.util.Log.d("WebDavBrowserVM", "Final URI: $finalUri")
        
        return finalUri
    }
    
    fun getAuthenticationInfo(): Pair<String, String>? {
        val server = _server.value ?: return null
        return if (server.username.isNotEmpty()) {
            Pair(server.username, server.password)
        } else {
            null
        }
    }
    
    /**
     * Extract base path from WebDAV server URL.
     * For AList URLs like "http://host:port/dav/", this returns "/dav"
     * For URLs like "http://host:port/", this returns "/"
     */
    private fun extractBasePath(url: String): String {
        return try {
            val urlObj = java.net.URL(url.trimEnd('/'))
            val path = urlObj.path.trimEnd('/')
            path.ifEmpty { "/" }
        } catch (e: Exception) {
            android.util.Log.w("WebDavBrowserVM", "Failed to extract base path from URL: $url", e)
            "/"  // Fallback to root
        }
    }
    
    /**
     * Encode path for WebDAV URLs - only encode necessary characters like spaces,
     * but keep safe characters like brackets, parentheses, etc.
     */
    private fun encodePathForWebDav(path: String): String {
        return path
            .replace(" ", "%20")  // Encode spaces
            // Keep other characters like [], (), &, etc. as they are safe in URLs
    }
    
    private fun loadFiles(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val serverId = _server.value?.id ?: return@launch
                val result = webDavRepository.getServerFiles(serverId, path)
                
                result.onSuccess { fileList ->
                    _allFiles.value = fileList
                }.onFailure { exception ->
                    _error.value = exception.message ?: "Failed to load files"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
