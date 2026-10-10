package dev.anilbeesetti.nextplayer.feature.network.screens.browse

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.MediaQuickSettingsDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import java.util.Date

@Composable
fun NetworkBrowseScreen(
    viewModel: NetworkBrowseViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NetworkBrowseScreenContent(
        state = state,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkBrowseScreenContent(
    state: NetworkBrowseUiState,
    onAction: (NetworkBrowseAction) -> Unit,
) {
    var showQuickSettings by rememberSaveable { mutableStateOf(false) }
    val isGrid = state.preferences.networkMediaLayoutMode == MediaLayoutMode.GRID
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = state.title,
                navigationIcon = {
                    FilledTonalIconButton(onClick = { onAction(NetworkBrowseAction.NavigateUp) }, modifier = Modifier.tvFocusRing()) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = { showQuickSettings = true },
                        modifier = Modifier.tvFocusRing(),
                    ) {
                        Icon(NextIcons.Sensitivity, contentDescription = stringResource(R.string.quick_settings))
                    }

                    if (!state.isLoading && state.error == null && state.files.any { !it.isDirectory }) {
                        FilledTonalIconButton(
                            onClick = { onAction(NetworkBrowseAction.PlayAll) },
                            modifier = Modifier.tvFocusRing(),
                        ) {
                            Icon(NextIcons.Play, contentDescription = stringResource(R.string.play_all))
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { scaffoldPadding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(scaffoldPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                val error = state.error
                Column(
                    modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.failed_to_load_folder),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = if (error.hostKeyMismatch != null) {
                            stringResource(R.string.host_key_mismatch)
                        } else {
                            error.message ?: stringResource(R.string.connection_failed)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    error.hostKeyMismatch?.let { mismatch ->
                        Spacer(Modifier.size(4.dp))
                        SelectionContainer {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.ssh_host_key_trusted_fingerprint,
                                        mismatch.trustedFingerprint,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.ssh_host_key_presented_fingerprint,
                                        mismatch.presentedFingerprint,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Button(onClick = { onAction(NetworkBrowseAction.Retry) }) { Text(stringResource(R.string.retry)) }
                }
            }

            else -> {
                val containerModifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding.copy(bottom = 0.dp))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background)

                BoxWithConstraints(modifier = containerModifier) {
                    val horizontalPadding = 8.dp
                    val itemSpacing = 2.dp
                    val availableWidth = maxWidth - horizontalPadding * 2 - itemSpacing
                    val folderMinWidth = if (isTv) 160.dp else 90.dp
                    val videoMinWidth = if (isTv) 240.dp else 130.dp
                    val folderColumns = if (isGrid) (availableWidth / folderMinWidth).toInt().coerceAtLeast(1) else 1
                    val videoColumns = if (isGrid) (availableWidth / videoMinWidth).toInt().coerceAtLeast(1) else 1
                    // A common column count lets folder and video tiles have different widths.
                    val columns = folderColumns * videoColumns
                    val folderCount = state.files.count { it.isDirectory }

                    if (state.files.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.empty_folder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier
                                .fillMaxSize()
                                .tvListFocus(),
                            contentPadding = PaddingValues(
                                start = horizontalPadding,
                                end = horizontalPadding,
                                top = 8.dp,
                                bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(itemSpacing),
                            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        ) {
                            itemsIndexed(
                                items = state.files,
                                key = { _, file -> file.path },
                                span = { _, file -> GridItemSpan(if (file.isDirectory) videoColumns else folderColumns) },
                            ) { index, file ->
                                NetworkFileItem(
                                    file = file,
                                    isGrid = isGrid,
                                    isFirstItem = index == 0 || (isGrid && index == folderCount),
                                    isLastItem = index == state.files.lastIndex || (isGrid && index == folderCount - 1),
                                    isRecentlyPlayed = state.preferences.markLastPlayedMedia &&
                                        (
                                            state.recentlyPlayedPath == file.path ||
                                                (file.isDirectory && state.recentlyPlayedPath?.startsWith("${file.path.trimEnd('/')}/") == true)
                                            ),
                                    playedPercentage = state.playbackHistory[file.path]?.playedPercentage
                                        ?.takeIf { state.preferences.showPlayedProgress },
                                    onClick = {
                                        if (file.isDirectory) onAction(NetworkBrowseAction.OpenFolder(file)) else onAction(NetworkBrowseAction.PlayVideo(file))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showQuickSettings) {
        MediaQuickSettingsDialog(
            layoutMode = state.preferences.networkMediaLayoutMode,
            sort = Sort(state.preferences.networkSortBy, state.preferences.networkSortOrder),
            sortOptions = listOf(Sort.By.TITLE, Sort.By.DATE, Sort.By.SIZE),
            onDismiss = { showQuickSettings = false },
            onConfirm = { layout, sort -> onAction(NetworkBrowseAction.UpdateQuickSettings(layout, sort)) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NetworkFileItem(
    file: NetworkFile,
    isGrid: Boolean,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    isRecentlyPlayed: Boolean,
    playedPercentage: Float?,
    onClick: () -> Unit,
) {
    NextSegmentedListItem(
        modifier = if (isGrid) Modifier.width(IntrinsicSize.Min) else Modifier,
        contentPadding = PaddingValues(8.dp),
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        colors = ListItemDefaults.segmentedColors(
            contentColor = if (isRecentlyPlayed) MaterialTheme.colorScheme.primary else ListItemDefaults.segmentedColors().contentColor,
            supportingContentColor = if (isRecentlyPlayed) MaterialTheme.colorScheme.primary else ListItemDefaults.segmentedColors().supportingContentColor,
        ),
        leadingContent = if (isGrid) {
            null
        } else {
            { NetworkFileThumbnail(file, playedPercentage, isGrid = false) }
        },
        content = {
            if (isGrid) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NetworkFileThumbnail(file, playedPercentage, isGrid = true)
                    Text(
                        text = file.name,
                        maxLines = 2,
                        style = MaterialTheme.typography.titleMedium.copy(lineBreak = LineBreak.Heading),
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = file.name,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        // Secondary line: size for videos, last-modified date for folders.
        supportingContent = when {
            isGrid -> null
            !file.isDirectory && file.size > 0 -> {
                { SupportingText(Utils.formatFileSize(file.size)) }
            }
            file.isDirectory && file.modified != null -> {
                val modified = file.modified!!
                { SupportingText(formatModifiedDate(modified)) }
            }
            else -> null
        },
    )
}

@Composable
private fun NetworkFileThumbnail(file: NetworkFile, playedPercentage: Float?, isGrid: Boolean) {
    if (file.isDirectory) {
        Box(
            modifier = if (isGrid) Modifier else Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.folder_thumb),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .width(if (isGrid) min(90.dp, LocalConfiguration.current.screenWidthDp.dp * 0.3f) else 72.dp)
                    .aspectRatio(20 / 17f),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .then(if (isGrid) Modifier.fillMaxWidth() else Modifier.width(86.dp))
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                .aspectRatio(16f / 10f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NextIcons.Video,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surfaceColorAtElevation(100.dp),
                modifier = Modifier.fillMaxSize(0.5f),
            )
            if (playedPercentage != null) {
                if (isGrid) {
                    Box(
                        modifier = Modifier.height(4.dp).fillMaxWidth().align(Alignment.BottomCenter),
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playedPercentage.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        progress = { playedPercentage.coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun formatModifiedDate(millis: Long): String {
    val context = LocalContext.current
    return remember(millis) {
        DateFormat.getMediumDateFormat(context).format(Date(millis))
    }
}
