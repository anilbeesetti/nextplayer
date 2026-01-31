package dev.anilbeesetti.nextplayer.feature.videopicker.screens.search

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.domain.SearchResults
import dev.anilbeesetti.nextplayer.core.domain.asRootFolder
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.FolderItem
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaView

@Composable
fun SearchRoute(
    viewModel: SearchViewModel = hiltViewModel(),
    onPlayVideo: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onFolderClick = onFolderClick,
        onVideoClick = onPlayVideo,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    uiState: SearchUiState,
    onNavigateUp: () -> Unit = {},
    onFolderClick: (String) -> Unit = {},
    onVideoClick: (Uri) -> Unit = {},
    onEvent: (SearchUiEvent) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = { onEvent(SearchUiEvent.OnQueryChange(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_videos_and_folders),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { onEvent(SearchUiEvent.OnQueryChange("")) }) {
                                    Icon(
                                        imageVector = NextIcons.Close,
                                        contentDescription = stringResource(R.string.clear_history),
                                    )
                                }
                            } else if (uiState.isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onEvent(SearchUiEvent.OnSearch(uiState.query))
                                keyboardController?.hide()
                            },
                        ),
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            errorBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                        ),
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                if (uiState.query.isBlank()) {
                    SuggestionsContent(
                        searchHistory = uiState.searchHistory,
                        popularFolders = uiState.popularFolders,
                        preferences = uiState.preferences,
                        onHistoryItemClick = { onEvent(SearchUiEvent.OnHistoryItemClick(it)) },
                        onRemoveHistoryItem = { onEvent(SearchUiEvent.OnRemoveHistoryItem(it)) },
                        onClearHistory = { onEvent(SearchUiEvent.OnClearHistory) },
                        onFolderClick = onFolderClick,
                    )
                } else {
                    SearchResultsContent(
                        searchResults = uiState.searchResults,
                        preferences = uiState.preferences,
                        isSearching = uiState.isSearching,
                        onFolderClick = onFolderClick,
                        onVideoClick = onVideoClick,
                        onVideoLoaded = { onEvent(SearchUiEvent.AddToSync(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionsContent(
    searchHistory: List<String>,
    popularFolders: List<Folder>,
    preferences: ApplicationPreferences,
    onHistoryItemClick: (String) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onFolderClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (searchHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListSectionTitle(
                        text = stringResource(R.string.recent_searches),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
                    )
                    TextButton(onClick = onClearHistory) {
                        Text(text = stringResource(R.string.clear_history))
                    }
                }
            }

            items(
                items = searchHistory,
                key = { "history_$it" },
            ) { query ->
                SearchHistoryItem(
                    query = query,
                    onClick = { onHistoryItemClick(query) },
                    onRemove = { onRemoveHistoryItem(query) },
                )
            }
        }

        if (popularFolders.isNotEmpty()) {
            item {
                ListSectionTitle(
                    text = stringResource(R.string.popular_folders),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = if (searchHistory.isNotEmpty()) 20.dp else 12.dp,
                        bottom = 8.dp,
                    ),
                )
            }

            itemsIndexed(
                items = popularFolders,
                key = { _, folder -> "popular_${folder.path}" },
            ) { index, folder ->
                FolderItem(
                    folder = folder,
                    isRecentlyPlayedFolder = false,
                    preferences = preferences.copy(mediaLayoutMode = MediaLayoutMode.LIST),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    index = index,
                    count = popularFolders.size,
                    onClick = { onFolderClick(folder.path) },
                )
            }
        }

        if (searchHistory.isEmpty() && popularFolders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = NextIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.search_videos_and_folders),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchHistoryItem(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    NextSegmentedListItem(
        modifier = Modifier.padding(horizontal = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = NextIcons.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = NextIcons.Close,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        content = {
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun SearchResultsContent(
    searchResults: SearchResults,
    preferences: ApplicationPreferences,
    isSearching: Boolean,
    onFolderClick: (String) -> Unit,
    onVideoClick: (Uri) -> Unit,
    onVideoLoaded: (Uri) -> Unit,
) {
    AnimatedVisibility(
        visible = isSearching,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 100.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            CircularProgressIndicator()
        }
    }

    AnimatedVisibility(
        visible = !isSearching,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        if (searchResults.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 100.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = NextIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.no_results_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            MediaView(
                rootFolder = searchResults.asRootFolder(),
                preferences = preferences,
                onFolderClick = onFolderClick,
                onVideoClick = onVideoClick,
                onVideoLoaded = onVideoLoaded,
                showHeaders = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SearchScreenEmptyPreview() {
    NextPlayerTheme {
        SearchScreen(
            uiState = SearchUiState(),
        )
    }
}

@PreviewLightDark
@Composable
private fun SearchScreenWithHistoryPreview() {
    NextPlayerTheme {
        SearchScreen(
            uiState = SearchUiState(
                searchHistory = listOf("avengers", "movie", "trailer"),
                popularFolders = listOf(
                    Folder(
                        name = "Movies",
                        path = "/storage/Movies",
                        dateModified = System.currentTimeMillis(),
                        mediaList = listOf(Video.sample, Video.sample),
                    ),
                    Folder(
                        name = "Downloads",
                        path = "/storage/Downloads",
                        dateModified = System.currentTimeMillis(),
                        mediaList = listOf(Video.sample),
                    ),
                ),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun SearchScreenWithResultsPreview() {
    NextPlayerTheme {
        SearchScreen(
            uiState = SearchUiState(
                query = "movie",
                searchResults = SearchResults(
                    folders = listOf(
                        Folder(
                            name = "Movies",
                            path = "/storage/Movies",
                            dateModified = System.currentTimeMillis(),
                        ),
                    ),
                    videos = listOf(
                        Video.sample.copy(nameWithExtension = "Movie_Clip.mp4", uriString = "content://sample/movie_clip.mp4"),
                        Video.sample.copy(nameWithExtension = "My_Movie.mp4", uriString = "content://sample/my_movie.mp4"),
                    ),
                ),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun SearchScreenNoResultsPreview() {
    NextPlayerTheme {
        SearchScreen(
            uiState = SearchUiState(
                query = "xyz123",
                searchResults = SearchResults(),
            ),
        )
    }
}
