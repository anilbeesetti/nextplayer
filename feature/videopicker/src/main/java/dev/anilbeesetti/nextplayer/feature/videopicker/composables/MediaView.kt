package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.domain.MediaHolder
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.findClosestFolder
import dev.anilbeesetti.nextplayer.core.model.recentPlayed
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.requestFocusUntilLanded
import dev.anilbeesetti.nextplayer.core.ui.components.restorableFocusItem
import dev.anilbeesetti.nextplayer.core.ui.components.thenIf
import dev.anilbeesetti.nextplayer.core.ui.extensions.plus
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionManager
import dev.anilbeesetti.nextplayer.feature.videopicker.state.rememberSelectionManager
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MediaView(
    mediaHolder: MediaHolder,
    recentlyPlayedVideo: Video?,
    recentlyPlayedFolder: Folder?,
    preferences: ApplicationPreferences,
    showHeaders: Boolean = preferences.mediaViewMode == MediaViewMode.FOLDER_TREE,
    contentPadding: PaddingValues = PaddingValues(),
    selectionManager: SelectionManager = rememberSelectionManager(),
    lazyGridState: LazyGridState = rememberLazyGridState(),
    firstItemFocusRequester: FocusRequester? = null,
    lastItemFocusRequester: FocusRequester? = null,
    lastItemDownFocusRequester: FocusRequester? = null,
    restoredFocusKey: String? = null,
    onItemFocused: ((String) -> Unit)? = null,
    onFolderClick: (String) -> Unit,
    onVideoClick: (Uri) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val folderMinWidth = if (isTv) 160.dp else 90.dp
    val videoMinWidth = if (isTv) 240.dp else 130.dp

    val firstItemRequester = remember { firstItemFocusRequester ?: FocusRequester() }
    val restoreRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    if (isTv) {
        LaunchedEffect(mediaHolder.folders.size, mediaHolder.videos.size) {
            if (hasRequestedInitialFocus) return@LaunchedEffect
            if (mediaHolder.folders.isEmpty() && mediaHolder.videos.isEmpty()) return@LaunchedEffect
            val hasRestore = restoredFocusKey != null && (
                mediaHolder.folders.any { it.path == restoredFocusKey } ||
                    mediaHolder.videos.any { it.uriString == restoredFocusKey }
                )
            // Prefer restoring the previously focused item; fall back to the first item.
            val targets = if (hasRestore) listOf(restoreRequester, firstItemRequester) else listOf(firstItemRequester)
            hasRequestedInitialFocus = targets.any { it.requestFocusUntilLanded() }
        }
    }

    // Wires a grid item into TV focus: the first item is the initial focus target, the last item
    // hands focus down to [lastItemDownFocusRequester], and every item participates in restoration.
    fun Modifier.tvItemFocus(isFirst: Boolean, isLast: Boolean, key: String): Modifier = this
        .thenIf(isTv && isFirst) { focusRequester(firstItemRequester) }
        .thenIf(isTv && isLast && lastItemFocusRequester != null) {
            focusRequester(lastItemFocusRequester!!)
                .thenIf(lastItemDownFocusRequester != null) {
                    focusProperties { down = lastItemDownFocusRequester!! }
                }
        }
        .restorableFocusItem(
            isTv = isTv,
            key = key,
            restoredKey = restoredFocusKey,
            restoreRequester = restoreRequester,
            onFocused = { onItemFocused?.invoke(it) },
        )

    BoxWithConstraints {
        val contentHorizontalPadding = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 8.dp
            MediaLayoutMode.GRID -> 8.dp
        }
        val itemSpacing = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 2.dp
            MediaLayoutMode.GRID -> 2.dp
        }
        val maxWidth = this.maxWidth - (contentHorizontalPadding * 2) - itemSpacing
        val maxFolders = (maxWidth / folderMinWidth).toInt()
        val maxVideos = (maxWidth / videoMinWidth).toInt()
        val spans = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> lcm(maxFolders, maxVideos)
        }

        val singleFolderSpan = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> spans / maxFolders
        }
        val singleVideoSpan = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> spans / maxVideos
        }

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = lazyGridState,
            columns = GridCells.Fixed(spans),
            contentPadding = contentPadding + PaddingValues(horizontal = contentHorizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (showHeaders && mediaHolder.folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(text = stringResource(id = R.string.folders) + " (${mediaHolder.folders.size})")
                }
            }
            itemsIndexed(
                items = mediaHolder.folders,
                key = { _, folder -> folder.path },
                span = { _, _ -> GridItemSpan(singleFolderSpan) },
            ) { index, folder ->
                val selected by remember { derivedStateOf { selectionManager.isFolderSelected(folder) } }
                FolderItem(
                    folder = folder,
                    isRecentlyPlayedFolder = folder.path == recentlyPlayedFolder?.path,
                    preferences = preferences,
                    modifier = Modifier.tvItemFocus(
                        isFirst = index == 0,
                        isLast = mediaHolder.videos.isEmpty() && index == mediaHolder.folders.lastIndex,
                        key = folder.path,
                    ),
                    selected = selected,
                    isFirstItem = index == 0,
                    isLastItem = index == mediaHolder.folders.lastIndex,
                    onClick = {
                        if (selectionManager.isInSelectionMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            selectionManager.toggleFolderSelection(folder)
                        } else {
                            onFolderClick(folder.path)
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectionManager.toggleFolderSelection(folder)
                    },
                )
            }

            if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE && mediaHolder.folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }

            if (showHeaders && mediaHolder.videos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(text = stringResource(id = R.string.videos) + " (${mediaHolder.videos.size})")
                }
            }

            itemsIndexed(
                items = mediaHolder.videos,
                key = { _, video -> video.uriString },
                span = { _, _ -> GridItemSpan(singleVideoSpan) },
            ) { index, video ->
                val selected by remember { derivedStateOf { selectionManager.isVideoSelected(video) } }
                VideoItem(
                    video = video,
                    preferences = preferences,
                    isRecentlyPlayedVideo = video.path == recentlyPlayedVideo?.path,
                    modifier = Modifier.tvItemFocus(
                        isFirst = index == 0 && mediaHolder.folders.isEmpty(),
                        isLast = index == mediaHolder.videos.lastIndex,
                        key = video.uriString,
                    ),
                    isFirstItem = index == 0,
                    isLastItem = index == mediaHolder.videos.lastIndex,
                    selected = selected,
                    onClick = {
                        if (selectionManager.isInSelectionMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            selectionManager.toggleVideoSelection(video)
                        } else {
                            onVideoClick(video.uriString.toUri())
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectionManager.toggleVideoSelection(video)
                    },
                )
            }
        }
    }
}

fun lcm(a: Int, b: Int): Int {
    return abs(a * b) / gcd(a, b)
}

fun gcd(a: Int, b: Int): Int {
    return if (b == 0) a else gcd(b, a % b)
}
