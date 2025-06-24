package dev.anilbeesetti.nextplayer.feature.videopicker.screens.webdav

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.WebDavFile
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import kotlinx.coroutines.delay

@Composable
fun WebDavBrowserRoute(
    serverId: String,
    onNavigateUp: () -> Unit,
    onPlayVideo: (Uri, String?, String?) -> Unit,
    viewModel: WebDavBrowserViewModel = hiltViewModel(),
) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortType by viewModel.sortType.collectAsStateWithLifecycle()
    // 移除derivedStateOf，因为canNavigateBack()已经返回了当前状态
    
    LaunchedEffect(serverId) {
        viewModel.loadServer(serverId)
    }
    
    WebDavBrowserScreen(
        files = files,
        isLoading = isLoading,
        error = error,
        currentPath = currentPath,
        searchQuery = searchQuery,
        sortType = sortType,
        onNavigateUp = {
            // 如果在WebDAV根目录，返回到WebDAV服务器列表
            // 否则导航到上级目录
            if (viewModel.canNavigateBack()) {
                viewModel.navigateBack()
            } else {
                onNavigateUp()
            }
        },
        onFileClick = { file ->
            if (file.isDirectory) {
                viewModel.navigateToPath(file.path)
            } else {
                // Create WebDAV URI for video playback (without embedded credentials)
                val uri = viewModel.createAuthenticatedUri(file.path)
                
                // Get authentication info if available
                val authInfo = viewModel.getAuthenticationInfo()
                
                onPlayVideo(uri, authInfo?.first, authInfo?.second)
            }
        },
        onBackClick = {
            if (viewModel.canNavigateBack()) {
                viewModel.navigateBack()
            } else {
                onNavigateUp()
            }
        },
        onRefresh = viewModel::refresh,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSortTypeChange = viewModel::updateSortType,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebDavBrowserScreen(
    files: List<WebDavFile>,
    isLoading: Boolean,
    error: String?,
    currentPath: String,
    searchQuery: String,
    sortType: SortType,
    onNavigateUp: () -> Unit,
    onFileClick: (WebDavFile) -> Unit,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val pullToRefreshState = rememberPullToRefreshState()
    
    // 搜索栏展开动效
    val searchBarHeight by animateDpAsState(
        targetValue = if (isSearchExpanded) 56.dp else 0.dp,
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "search_bar_height"
    )
    
    val searchBarAlpha by animateFloatAsState(
        targetValue = if (isSearchExpanded) 1f else 0f,
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "search_bar_alpha"
    )
    
    // 当搜索展开时自动聚焦
    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            delay(150) // 等待动画进行一半
            searchFocusRequester.requestFocus()
        } else {
            keyboardController?.hide()
            onSearchQueryChange("") // 清空搜索内容
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 动画切换标题和搜索栏
                    AnimatedContent(
                        targetState = isSearchExpanded,
                        transitionSpec = {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                        },
                        label = "title_search_transition"
                    ) { expanded ->
                        if (expanded) {
                            // 搜索栏
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { 
                                    Text(
                                        "搜索文件...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ) 
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = NextIcons.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(
                                            onClick = { onSearchQueryChange("") }
                                        ) {
                                            Icon(
                                                imageVector = NextIcons.Delete,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                            )
                        } else {
                            // 标题
                            Text(
                                text = if (currentPath == "/") "WebDAV" else currentPath,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    // 搜索按钮
                    IconButton(
                        onClick = { 
                            isSearchExpanded = !isSearchExpanded
                        }
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) NextIcons.ArrowBack else NextIcons.Search,
                            contentDescription = if (isSearchExpanded) "Close Search" else "Search",
                        )
                    }
                    
                    // 排序按钮
                    AnimatedVisibility(
                        visible = !isSearchExpanded,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(
                                imageVector = NextIcons.Sort,
                                contentDescription = "Sort",
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = NextIcons.Priority,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Error loading files",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                }
                
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                files.filter { file -> file.isDirectory || isVideoFile(file) }.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = NextIcons.FolderOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No video files or folders found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                
                else -> {
                    val filteredFiles = files.filter { file ->
                        // 过滤掉当前目录引用，避免显示root或当前文件夹
                        val isCurrentDirectory = file.path.trimEnd('/') == currentPath.trimEnd('/') ||
                                               (file.name.isEmpty() && file.path == currentPath)
                        
                        !isCurrentDirectory && (file.isDirectory || isVideoFile(file))
                    }
                    
                    // 使用PullToRefresh实现下拉刷新
                    PullToRefreshBox(
                        state = pullToRefreshState,
                        isRefreshing = isLoading,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredFiles) { file ->
                                WebDavFileItem(
                                    file = file,
                                    onClick = { onFileClick(file) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 排序对话框
    if (showSortDialog) {
        SortDialog(
            currentSortType = sortType,
            onSortTypeSelected = onSortTypeChange,
            onDismiss = { showSortDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavFileItem(
    file: WebDavFile,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (file.isDirectory) NextIcons.Folder else NextIcons.Video,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary 
                      else MaterialTheme.colorScheme.secondary,
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!file.isDirectory && file.size > 0) {
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

@Composable
private fun SortDialog(
    currentSortType: SortType,
    onSortTypeSelected: (SortType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column {
                SortType.values().forEach { sortType ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSortTypeSelected(sortType)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSortType == sortType,
                            onClick = {
                                onSortTypeSelected(sortType)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sortType.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun isVideoFile(file: WebDavFile): Boolean {
    if (file.isDirectory) return false
    
    val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "3g2",
        "asf", "divx", "f4v", "m2ts", "m2v", "mts", "ogv", "rm", "rmvb", "ts",
        "vob", "xvid", "mpg", "mpeg", "mp2", "mpe", "mpv", "m1v", "m2p", "ps"
    )
    
    val extension = file.name.substringAfterLast('.', "").lowercase()
    return extension in videoExtensions || file.mimeType?.startsWith("video/") == true
}
