package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.FoldersState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersView(
    foldersState: FoldersState,
    preferences: ApplicationPreferences,
    onFolderClick: (String) -> Unit,
    onDeleteFolderClick: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var showFolderActionsFor: Folder? by rememberSaveable { mutableStateOf(null) }
    var deleteAction: Folder? by rememberSaveable { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (foldersState) {
        FoldersState.Loading -> CenterCircularProgressBar()
        is FoldersState.Success -> {
            MediaLazyList {
                if (foldersState.data.isEmpty()) {
                    item { NoVideosFound() }
                } else {
                    items(foldersState.data, key = { it.path }) {
                        FolderItem(
                            folder = it,
                            isRecentlyPlayedFolder = foldersState.recentPlayedVideo in it.mediaList,
                            preferences = preferences,
                            modifier = Modifier.combinedClickable(
                                onClick = { onFolderClick(it.path) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showFolderActionsFor = it
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    showFolderActionsFor?.let {
        OptionsBottomSheet(
            title = it.name,
            onDismiss = { showFolderActionsFor = null },
        ) {
            BottomSheetItem(
                text = stringResource(R.string.delete),
                icon = NextIcons.Delete,
                onClick = {
                    deleteAction = it
                    scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                        if (!bottomSheetState.isVisible) showFolderActionsFor = null
                    }
                },
            )
        }
    }

    deleteAction?.let {
        DeleteConfirmationDialog(
            subText = stringResource(R.string.delete_folder),
            onCancel = { deleteAction = null },
            onConfirm = {
                onDeleteFolderClick(it.path)
                deleteAction = null
            },
            fileNames = listOf(it.name),
        )
    }
}
