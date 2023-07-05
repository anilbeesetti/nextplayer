package dev.anilbeesetti.nextplayer.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
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
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

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
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.appearance_name),
                    description = stringResource(id = R.string.appearance_description),
                    icon = NextIcons.Appearance,
                    onClick = { onItemClick(Setting.APPEARANCE) }
                )
            }
            item {
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.media_library),
                    description = stringResource(id = R.string.media_library_description),
                    icon = NextIcons.Movie,
                    onClick = { onItemClick(Setting.MEDIA_LIBRARY) }
                )
            }
            item {
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.player_name),
                    description = stringResource(id = R.string.player_description),
                    icon = NextIcons.Player,
                    onClick = { onItemClick(Setting.PLAYER) }
                )
            }
            item {
                ClickablePreferenceItem(
                    title = stringResource(R.string.decoder),
                    description = stringResource(R.string.decoder_desc),
                    icon = NextIcons.Decoder,
                    onClick = { onItemClick(Setting.DECODER) }
                )
            }
            item {
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.subtitle),
                    description = stringResource(R.string.subtitle_desc),
                    icon = NextIcons.Subtitle,
                    onClick = { onItemClick(Setting.SUBTITLE) }
                )
            }
            item {
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.about_name),
                    description = stringResource(id = R.string.about_description),
                    icon = NextIcons.Info,
                    onClick = { onItemClick(Setting.ABOUT) }
                )
            }
        }
    }
}

enum class Setting {
    APPEARANCE,
    MEDIA_LIBRARY,
    PLAYER,
    DECODER,
    SUBTITLE,
    ABOUT
}
