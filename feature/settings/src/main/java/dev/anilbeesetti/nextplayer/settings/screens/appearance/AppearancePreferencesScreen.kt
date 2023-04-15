package dev.anilbeesetti.nextplayer.settings.screens.appearance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
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
import dev.anilbeesetti.nextplayer.core.datastore.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitchWithDivider
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearancePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: AppearancePreferencesViewModel = hiltViewModel(),
) {

    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.appearance_name),
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
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = innerPadding,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                item {
                    PreferenceSubtitle(text = stringResource(id = R.string.appearance_name))
                }
                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(id = R.string.dark_theme),
                        description = preferences.themeConfig.value,
                        isChecked = preferences.themeConfig == ThemeConfig.DARK,
                        onChecked = viewModel::toggleDarkTheme,
                        icon = NextIcons.DarkMode,
                        onClick = {
                            viewModel.onEvent(
                                AppearancePreferencesEvent.ShowDialog(
                                    AppearancePreferenceDialog.Theme
                                )
                            )
                        }
                    )
                }
            }
            when (uiState.showDialog) {
                AppearancePreferenceDialog.Theme -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.dark_theme),
                        onDismissClick = {
                            viewModel.onEvent(
                                AppearancePreferencesEvent.ShowDialog(AppearancePreferenceDialog.None)
                            )
                        }
                    ) {
                        ThemeConfig.values().forEach {
                            RadioTextButton(
                                text = it.value,
                                selected = (it == preferences.themeConfig),
                                onClick = {
                                    viewModel.updateThemeConfig(it)
                                    viewModel.onEvent(
                                        AppearancePreferencesEvent.ShowDialog(
                                            AppearancePreferenceDialog.None
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                AppearancePreferenceDialog.None -> { /* Do nothing */
                }
            }
        }
    }
}