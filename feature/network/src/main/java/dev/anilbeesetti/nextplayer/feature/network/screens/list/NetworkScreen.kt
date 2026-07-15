package dev.anilbeesetti.nextplayer.feature.network.screens.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.requestFocusUntilLanded
import dev.anilbeesetti.nextplayer.core.ui.components.rememberTvListFocusRequester
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@Composable
fun NetworkScreenRoute(
    onAddConnection: () -> Unit,
    onEditConnection: (Long) -> Unit,
    onOpenConnection: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: NetworkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NetworkScreen(
        uiState = uiState,
        onAddConnection = onAddConnection,
        onEditConnection = onEditConnection,
        onOpenConnection = onOpenConnection,
        onSettingsClick = onSettingsClick,
        onDeleteConnection = viewModel::deleteConnection,
    )
}

@Composable
internal fun NetworkScreen(
    uiState: NetworkUiState,
    onAddConnection: () -> Unit,
    onEditConnection: (Long) -> Unit,
    onOpenConnection: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onDeleteConnection: (Long) -> Unit,
) {
    var connectionToDelete by remember { mutableStateOf<NetworkConnection?>(null) }

    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val showEmptyState = uiState.connections.isEmpty() && !uiState.isLoading
    // The connection list grabs D-pad focus via tvListFocus, but the empty state has nothing
    // focusable, so focus would fall back to the Home tab. Land it on the Add-connection button.
    val addConnectionFocusRequester = remember { FocusRequester() }
    if (isTv) {
        LaunchedEffect(showEmptyState) {
            if (showEmptyState) addConnectionFocusRequester.requestFocusUntilLanded()
        }
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(R.string.network),
                fontWeight = FontWeight.Bold,
                actions = {
                    IconButton(onClick = onSettingsClick, modifier = Modifier.tvFocusRing()) {
                        Icon(
                            imageVector = NextIcons.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddConnection,
                icon = { Icon(NextIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_connection)) },
                modifier = Modifier
                    .focusRequester(addConnectionFocusRequester)
                    .tvFocusRing(shape = RoundedCornerShape(16.dp)),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        val containerModifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .padding(start = padding.calculateStartPadding(LocalLayoutDirection.current) + 2.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.background)

        Box(modifier = containerModifier) {
            if (showEmptyState) {
                NetworkEmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvListFocus(rememberTvListFocusRequester()),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = uiState.connections,
                        key = { _, connection -> connection.id },
                    ) { index, connection ->
                        ConnectionItem(
                            connection = connection,
                            isFirstItem = index == 0,
                            isLastItem = index == uiState.connections.lastIndex,
                            onClick = { onOpenConnection(connection.id) },
                            onEdit = { onEditConnection(connection.id) },
                            onDelete = { connectionToDelete = connection },
                        )
                    }
                }
            }
        }
    }

    connectionToDelete?.let { connection ->
        NextDialog(
            onDismissRequest = { connectionToDelete = null },
            title = { Text(stringResource(R.string.delete_connection)) },
            content = { Text(stringResource(R.string.delete_connection_confirmation, connection.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConnection(connection.id)
                        connectionToDelete = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { connectionToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectionItem(
    connection: NetworkConnection,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    NextSegmentedListItem(
        contentPadding = PaddingValues(8.dp),
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        leadingContent = {
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = connection.protocol.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        content = {
            Text(
                text = connection.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = "${connection.protocol.name} · ${connection.host}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(NextIcons.ExtraSettings, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        leadingIcon = { Icon(NextIcons.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(NextIcons.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun NetworkEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = NextIcons.Network,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.no_connections_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.no_connections_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun NetworkProtocol.icon(): ImageVector = when (this) {
    NetworkProtocol.SMB -> NextIcons.Storage
    NetworkProtocol.FTP -> NextIcons.Dns
    NetworkProtocol.WEBDAV -> NextIcons.Cloud
}

@PreviewLightDark
@Composable
private fun NetworkScreenPreview() {
    NextPlayerTheme {
        NetworkScreen(
            uiState = NetworkUiState(connections = listOf(NetworkConnection.sample), isLoading = false),
            onAddConnection = {},
            onEditConnection = {},
            onOpenConnection = {},
            onSettingsClick = {},
            onDeleteConnection = {},
        )
    }
}
