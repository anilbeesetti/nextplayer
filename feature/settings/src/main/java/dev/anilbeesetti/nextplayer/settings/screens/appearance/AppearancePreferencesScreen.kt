package dev.anilbeesetti.nextplayer.settings.screens.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
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
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitchWithDivider
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.supportsDynamicTheming
import dev.anilbeesetti.nextplayer.settings.composables.OptionsDialog
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSubtitle
import dev.anilbeesetti.nextplayer.settings.extensions.name

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
        modifier = Modifier
            .nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            // TODO: Check why the appbar flickers when changing the theme with small appbar and not with large appbar
            NextTopAppBar(
                title = stringResource(id = R.string.appearance_name),
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
            DarkThemeSetting(
                currentPreference = preferences.themeConfig,
                onChecked = viewModel::toggleDarkTheme,
                onClick = { viewModel.showDialog(AppearancePreferenceDialog.Theme) },
            )
            HighContrastDarkThemeSetting(
                isChecked = preferences.useHighContrastDarkTheme,
                onClick = viewModel::toggleUseHighContrastDarkTheme,
            )
            DynamicThemingSetting(
                isChecked = preferences.useDynamicColors,
                onClick = viewModel::toggleUseDynamicColors,
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                AppearancePreferenceDialog.Theme -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.dark_theme),
                        onDismissClick = viewModel::hideDialog,
                    ) {
                        items(ThemeConfig.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                selected = (it == preferences.themeConfig),
                                onClick = {
                                    viewModel.updateThemeConfig(it)
                                    viewModel.hideDialog()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DarkThemeSetting(
    currentPreference: ThemeConfig,
    onChecked: () -> Unit,
    onClick: () -> Unit,
) {
    PreferenceSwitchWithDivider(
        title = stringResource(id = R.string.dark_theme),
        description = currentPreference.name(),
        isChecked = currentPreference == ThemeConfig.ON,
        onChecked = onChecked,
        icon = NextIcons.DarkMode,
        onClick = onClick,
    )
}

@Composable
fun HighContrastDarkThemeSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    PreferenceSwitch(
        title = stringResource(R.string.high_contrast_dark_theme),
        description = stringResource(R.string.high_contrast_dark_theme_desc),
        isChecked = isChecked,
        onClick = onClick,
        icon = NextIcons.Contrast,
    )
}

@Composable
fun DynamicThemingSetting(
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    if (supportsDynamicTheming()) {
        PreferenceSwitch(
            title = stringResource(id = R.string.dynamic_theme),
            description = stringResource(id = R.string.dynamic_theme_description),
            isChecked = isChecked,
            onClick = onClick,
            icon = NextIcons.Appearance,
        )
    }
}
