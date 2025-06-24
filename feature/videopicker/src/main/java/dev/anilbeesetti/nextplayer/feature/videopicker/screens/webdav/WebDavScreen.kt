package dev.anilbeesetti.nextplayer.feature.videopicker.screens.webdav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.WebDavServer
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun WebDavRoute(
    onNavigateUp: () -> Unit,
    onServerClick: (WebDavServer) -> Unit,
    viewModel: WebDavViewModel = hiltViewModel(),
) {
    val servers by viewModel.filteredServers.collectAsStateWithLifecycle(initialValue = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val testConnectionResult by viewModel.testConnectionResult.collectAsStateWithLifecycle()

    WebDavScreen(
        servers = servers,
        searchQuery = searchQuery,
        isLoading = isLoading,
        testConnectionResult = testConnectionResult,
        onNavigateUp = onNavigateUp,
        onServerClick = onServerClick,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onAddServer = viewModel::addServer,
        onDeleteServer = viewModel::deleteServer,
        onTestConnection = viewModel::testConnection,
        onClearTestResult = viewModel::clearTestConnectionResult,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebDavScreen(
    servers: List<WebDavServer>,
    searchQuery: String,
    isLoading: Boolean,
    testConnectionResult: TestConnectionResult?,
    onNavigateUp: () -> Unit,
    onServerClick: (WebDavServer) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddServer: (WebDavServer) -> Unit,
    onDeleteServer: (String) -> Unit,
    onTestConnection: (WebDavServer) -> Unit,
    onClearTestResult: () -> Unit,
) {
    var showAddServerDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            NextCenterAlignedTopAppBar(
                title = "WebDAV",
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddServerDialog = true }) {
                        Icon(
                            imageVector = NextIcons.Add,
                            contentDescription = "Add Server",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search servers...") },
                leadingIcon = {
                    Icon(
                        imageVector = NextIcons.Search,
                        contentDescription = "Search",
                    )
                },
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (servers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = NextIcons.WebDav,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No WebDAV servers added",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the + button to add a server",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(servers) { server ->
                        WebDavServerItem(
                            server = server,
                            onClick = { onServerClick(server) },
                            onDelete = { onDeleteServer(server.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAddServerDialog) {
        AddServerDialog(
            onDismiss = {
                showAddServerDialog = false
                onClearTestResult()
            },
            onAddServer = { server ->
                onAddServer(server)
                showAddServerDialog = false
                onClearTestResult()
            },
            onTestConnection = onTestConnection,
            testConnectionResult = testConnectionResult,
            isTestingConnection = isLoading,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavServerItem(
    server: WebDavServer,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                imageVector = NextIcons.WebDav,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (server.isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = server.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (server.lastConnected > 0) {
                    Text(
                        text = "Last connected: ${formatTimestamp(server.lastConnected)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = NextIcons.Delete,
                    contentDescription = "Delete Server",
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Server") },
            text = { Text("Are you sure you want to delete '${server.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    // Simple timestamp formatting - you might want to use a proper date formatter
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val hours = diff / (1000 * 60 * 60)
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        else -> "Recently"
    }
}
