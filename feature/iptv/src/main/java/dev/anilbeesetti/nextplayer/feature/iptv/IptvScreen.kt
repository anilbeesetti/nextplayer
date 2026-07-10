package dev.anilbeesetti.nextplayer.feature.iptv

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.anilbeesetti.nextplayer.core.model.IptvChannel
import dev.anilbeesetti.nextplayer.core.model.IptvPlaylist
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Composable
fun IptvScreenRoute(
    onNavigateUp: () -> Unit,
    onPlayChannel: (IptvChannel) -> Unit,
    viewModel: IptvViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is IptvEvent.PlayChannel -> onPlayChannel(event.channel)
            is IptvEvent.ImportSucceeded -> Toast.makeText(
                context,
                context.getString(R.string.iptv_import_success, event.channelCount),
                Toast.LENGTH_SHORT,
            ).show()

            is IptvEvent.ImportFailed -> Toast.makeText(
                context,
                context.getString(R.string.iptv_import_failed, event.message),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    IptvScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IptvScreen(
    uiState: IptvUiState,
    onNavigateUp: () -> Unit,
    onAction: (IptvAction) -> Unit,
) {
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var qualityPickerFor: ChannelListItem? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(R.string.iptv),
                fontWeight = FontWeight.Bold,
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = NextIcons.PlaylistAdd,
                            contentDescription = stringResource(R.string.add_playlist),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.playlists.isNotEmpty()) {
                PlaylistFilterRow(
                    playlists = uiState.playlists,
                    selectedPlaylistId = uiState.selectedPlaylistId,
                    onSelect = { onAction(IptvAction.SelectPlaylist(it)) },
                    onDelete = { onAction(IptvAction.DeletePlaylist(it)) },
                    onRefresh = { onAction(IptvAction.RefreshPlaylist(it)) },
                )
            }

            when {
                uiState.isEmpty -> EmptyState(
                    onImportClick = { showImportDialog = true },
                    isImporting = uiState.isImporting,
                )

                uiState.channels.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_channels_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> ChannelList(
                    channels = uiState.channels,
                    onChannelClick = { item ->
                        if (item.hasMultipleQualities) {
                            qualityPickerFor = item
                        } else {
                            onAction(IptvAction.PlayChannel(item.defaultVariant.channel))
                        }
                    },
                )
            }
        }
    }

    if (showImportDialog) {
        ImportPlaylistDialog(
            isImporting = uiState.isImporting,
            onDismiss = { showImportDialog = false },
            onImportUrl = { url, name ->
                onAction(IptvAction.ImportUrl(url, name))
                showImportDialog = false
            },
            onImportContent = { content, name, source ->
                onAction(IptvAction.ImportContent(content, name, source))
                showImportDialog = false
            },
        )
    }

    qualityPickerFor?.let { item ->
        QualityPickerDialog(
            item = item,
            onDismiss = { qualityPickerFor = null },
            onSelect = { channel ->
                qualityPickerFor = null
                onAction(IptvAction.PlayChannel(channel))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistFilterRow(
    playlists: List<IptvPlaylist>,
    selectedPlaylistId: Long?,
    onSelect: (Long?) -> Unit,
    onDelete: (Long) -> Unit,
    onRefresh: (IptvPlaylist) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedPlaylistId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.all_channels)) },
        )
        playlists.forEach { playlist ->
            FilterChip(
                selected = selectedPlaylistId == playlist.id,
                onClick = { onSelect(playlist.id) },
                label = { Text("${playlist.name} (${playlist.channelCount})") },
            )
        }
        val selected = playlists.firstOrNull { it.id == selectedPlaylistId }
        if (selected != null) {
            IconButton(onClick = { onRefresh(selected) }) {
                Icon(
                    imageVector = NextIcons.Refresh,
                    contentDescription = stringResource(R.string.refresh_playlist),
                )
            }
            IconButton(onClick = { onDelete(selected.id) }) {
                Icon(
                    imageVector = NextIcons.Delete,
                    contentDescription = stringResource(R.string.delete_playlist),
                )
            }
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<ChannelListItem>,
    onChannelClick: (ChannelListItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            items = channels,
            key = { _, item -> item.defaultVariant.channel.id.toString() + item.displayName },
        ) { index, item ->
            ChannelItem(
                item = item,
                isFirstItem = index == 0,
                isLastItem = index == channels.lastIndex,
                onClick = { onChannelClick(item) },
            )
        }
    }
}

@Composable
private fun ChannelItem(
    item: ChannelListItem,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    onClick: () -> Unit,
) {
    NextSegmentedListItem(
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        leadingContent = { ChannelLogo(logoUrl = item.logoUrl) },
        content = {
            Text(
                text = item.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.isLive) LiveBadge()
                item.groupTitle?.let {
                    Text(
                        text = it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = if (item.hasMultipleQualities) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = NextIcons.Quality,
                        contentDescription = stringResource(R.string.select_quality),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.defaultVariant.quality.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun ChannelLogo(logoUrl: String?) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = shape,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = NextIcons.LiveTv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    Surface(
        color = Color(0xFFE53935),
        shape = CircleShape,
    ) {
        Text(
            text = stringResource(R.string.live),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState(
    onImportClick: () -> Unit,
    isImporting: Boolean,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = NextIcons.LiveTv,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.no_iptv_playlists),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.no_iptv_playlists_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (isImporting) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onImportClick) {
                    Icon(imageVector = NextIcons.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_playlist))
                }
            }
        }
    }
}

/** Lifecycle-aware one-shot event collector, mirroring the videopicker helper. */
@Composable
private fun <T> ObserveAsEvents(flow: Flow<T>, onEvent: suspend (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                flow.collect(onEvent)
            }
        }
    }
}
