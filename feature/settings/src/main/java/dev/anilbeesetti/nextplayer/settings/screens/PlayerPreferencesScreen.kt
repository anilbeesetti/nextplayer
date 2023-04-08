package dev.anilbeesetti.nextplayer.settings.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.datastore.Resume
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialogDefaults
import dev.anilbeesetti.nextplayer.core.ui.components.RadioTextButton
import dev.anilbeesetti.nextplayer.feature.settings.R
import dev.anilbeesetti.nextplayer.settings.composables.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.settings.composables.PreferenceSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: PlayerPreferencesViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehaviour = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviour.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.player_name)
                    )
                },
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
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = innerPadding,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                item {
                    ClickablePreferenceItem(
                        title = stringResource(id = R.string.resume),
                        description = stringResource(id = R.string.resume_description),
                        onClick = { viewModel.onEvent(PlayerPreferencesEvent.ResumeDialog(true)) }
                    )
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.remember_brightness_level),
                        description = stringResource(
                            id = R.string.remember_brightness_level_description
                        ),
                        isChecked = preferences.rememberPlayerBrightness,
                        onClick = viewModel::toggleRememberBrightnessLevel
                    )
                }
            }
            if (uiState.showResumeDialog) {
                NextDialog(
                    onDismissRequest = {
                        viewModel.onEvent(
                            PlayerPreferencesEvent.ResumeDialog(false)
                        )
                    },
                    title = {
                        Text(
                            text = stringResource(id = R.string.resume)
                        )
                    },
                    content = {
                        Column(
                            modifier = Modifier.selectableGroup()
                        ) {
                            Resume.values().forEach {
                                RadioTextButton(
                                    text = it.name,
                                    selected = (it == preferences.resume),
                                    onClick = {
                                        viewModel.updateResume(it)
                                        viewModel.onEvent(
                                            PlayerPreferencesEvent.ResumeDialog(false)
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = NextDialogDefaults.dialogPadding)
                                )
                            }
                        }
                    },
                    dismissButton = {
                        CancelButton(
                            onClick = {
                                viewModel.onEvent(
                                    PlayerPreferencesEvent.ResumeDialog(false)
                                )
                            }
                        )
                    },
                    confirmButton = null
                )
            }
        }
    }
}
