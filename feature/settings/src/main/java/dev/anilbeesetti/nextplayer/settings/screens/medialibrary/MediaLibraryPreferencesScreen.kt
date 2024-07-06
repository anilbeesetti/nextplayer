package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryPreferencesScreen(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit = {},
    viewModel: MediaLibraryPreferencesViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.media_library),
                scrollBehavior = scrollBehaviour,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(state = rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            PreferenceSubtitle(text = stringResource(id = R.string.appearance_name))
            MarkLastPlayedMediaSetting(
                isChecked = preferences.markLastPlayedMedia,
                onClick = viewModel::toggleMarkLastPlayedMedia,
            )
            FloatingPlayButtonSetting(
                isChecked = preferences.showFloatingPlayButton,
                onClick = viewModel::toggleShowFloatingPlayButton,
            )

            PreferenceSubtitle(text = stringResource(id = R.string.scan))
            HideFoldersSettings(
                onClick = onFolderSettingClick,
            )
        }
    }
}

@Composable
fun HideFoldersSettings(
    onClick: () -> Unit,
) {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.manage_folders),
        description = stringResource(id = R.string.manage_folders_desc),
        icon = NextIcons.FolderOff,
        onClick = onClick,
    )
}

@Composable
fun MarkLastPlayedMediaSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.mark_last_played_media),
        description = stringResource(
            id = R.string.mark_last_played_media_desc,
        ),
        icon = NextIcons.Check,
        isChecked = isChecked,
        onClick = onClick,
    )
}

@Composable
fun FloatingPlayButtonSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(id = R.string.floating_play_button),
        description = stringResource(
            id = R.string.floating_play_button_desc,
        ),
        icon = NextIcons.SmartButton,
        isChecked = isChecked,
        onClick = onClick,
    )
}
