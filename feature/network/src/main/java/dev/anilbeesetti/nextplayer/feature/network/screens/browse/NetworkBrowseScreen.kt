package dev.anilbeesetti.nextplayer.feature.network.screens.browse

import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.rememberTvListFocusRequester
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.network.ObserveAsEvents
import java.util.Date

@Composable
fun NetworkBrowseScreenRoute(
    onNavigateUp: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
    onNavigateToFolder: (connectionId: Long, path: String) -> Unit,
    viewModel: NetworkBrowseViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.playEvents) { uri -> onPlayVideo(uri) }

    NetworkBrowseScreen(
        uiState = uiState,
        onBack = onNavigateUp,
        onFolderClick = { file -> onNavigateToFolder(viewModel.connectionId, file.path) },
        onVideoClick = viewModel::playVideo,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkBrowseScreen(
    uiState: NetworkBrowseUiState,
    onBack: () -> Unit,
    onFolderClick: (NetworkFile) -> Unit,
    onVideoClick: (NetworkFile) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = uiState.title,
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack, modifier = Modifier.tvFocusRing()) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.failed_to_load_folder),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }

            else -> {
                val containerModifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .padding(start = padding.calculateStartPadding(LocalLayoutDirection.current) + 2.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background)

                Box(modifier = containerModifier) {
                    if (uiState.files.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.empty_folder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .tvListFocus(rememberTvListFocusRequester()),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 8.dp,
                                bottom = padding.calculateBottomPadding() + 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(
                                items = uiState.files,
                                key = { _, file -> file.path },
                            ) { index, file ->
                                NetworkFileItem(
                                    file = file,
                                    isFirstItem = index == 0,
                                    isLastItem = index == uiState.files.lastIndex,
                                    onClick = {
                                        if (file.isDirectory) onFolderClick(file) else onVideoClick(file)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NetworkFileItem(
    file: NetworkFile,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    onClick: () -> Unit,
) {
    NextSegmentedListItem(
        contentPadding = PaddingValues(8.dp),
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        leadingContent = {
            if (file.isDirectory) {
                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.folder_thumb),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .width(72.dp)
                            .aspectRatio(20 / 17f),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(86.dp)
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
                }
            }
        },
        content = {
            Text(
                text = file.name,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // Secondary line: size for videos, last-modified date for folders.
        supportingContent = when {
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
private fun SupportingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun formatModifiedDate(millis: Long): String {
    val context = LocalContext.current
    return remember(millis) {
        DateFormat.getMediumDateFormat(context).format(Date(millis))
    }
}
