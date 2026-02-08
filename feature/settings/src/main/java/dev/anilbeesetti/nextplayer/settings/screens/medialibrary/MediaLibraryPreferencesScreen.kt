package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@Composable
fun MediaLibraryPreferencesScreen(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit = {},
    viewModel: MediaLibraryPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaLibraryPreferencesContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onFolderSettingClick = onFolderSettingClick,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaLibraryPreferencesContent(
    uiState: MediaLibraryPreferencesUiState,
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit,
    onEvent: (MediaLibraryPreferencesUiEvent) -> Unit,
) {
    val preferences = uiState.preferences

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.media_library),
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = stringResource(id = R.string.media_library))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    title = stringResource(id = R.string.mark_last_played_media),
                    description = stringResource(
                        id = R.string.mark_last_played_media_desc,
                    ),
                    icon = NextIcons.Check,
                    isChecked = preferences.markLastPlayedMedia,
                    onClick = { onEvent(MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia) },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.scan))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.manage_folders),
                    description = stringResource(id = R.string.manage_folders_desc),
                    icon = NextIcons.FolderOff,
                    onClick = onFolderSettingClick,
                    isFirstItem = true,
                    isLastItem = true,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MediaLibraryPreferencesScreenPreview() {
    NextPlayerTheme {
        MediaLibraryPreferencesContent(
            uiState = MediaLibraryPreferencesUiState(),
            onNavigateUp = {},
            onFolderSettingClick = {},
            onEvent = {},
        )
    }
}
