package dev.anilbeesetti.nextplayer.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Aod
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.settings.composables.SettingGroupItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onItemClick: (Setting) -> Unit
) {
    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.settings),
                scrollBehavior = scrollBehaviour,
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
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
                SettingGroupItem(
                    title = stringResource(id = R.string.interface_name),
                    description = stringResource(id = R.string.interface_description),
                    icon = Icons.Rounded.Aod,
                    onClick = { onItemClick(Setting.INTERFACE) }
                )
            }
            item {
                SettingGroupItem(
                    title = stringResource(id = R.string.player_name),
                    description = stringResource(id = R.string.player_description),
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { onItemClick(Setting.PLAYER) }
                )
            }
            item {
                SettingGroupItem(
                    title = stringResource(id = R.string.about_name),
                    description = stringResource(id = R.string.about_description),
                    icon = Icons.Rounded.Info,
                    onClick = { onItemClick(Setting.ABOUT) }
                )
            }
        }
    }
}

enum class Setting {
    INTERFACE,
    PLAYER,
    ABOUT
}
