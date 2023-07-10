package dev.anilbeesetti.nextplayer.settings.screens.medialibrary

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.common.extensions.scanStorage
import dev.anilbeesetti.nextplayer.core.common.extensions.showToast
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryPreferencesScreen(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit = {}
) {
    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.media_library),
                scrollBehavior = scrollBehaviour,
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
        ) {
            item {
                PreferenceSubtitle(text = stringResource(id = R.string.scan))
            }
            hideFoldersSettings(
                onClick = onFolderSettingClick
            )
            forceRescanStorageSetting(
                onClick = {
                    context.scanStorage()
                    context.showToast(
                        string = context.getString(R.string.scanning_storage),
                        duration = Toast.LENGTH_LONG
                    )
                }
            )
        }
    }
}

fun LazyListScope.hideFoldersSettings(
    onClick: () -> Unit
) = item {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.manage_folders),
        description = stringResource(id = R.string.manage_folders_desc),
        icon = NextIcons.FolderOff,
        onClick = onClick
    )
}


fun LazyListScope.forceRescanStorageSetting(
    onClick: () -> Unit
) = item {
    ClickablePreferenceItem(
        title = stringResource(id = R.string.force_rescan_storage),
        description = stringResource(id = R.string.force_rescan_storage_desc),
        icon = NextIcons.Update,
        onClick = onClick
    )
}
