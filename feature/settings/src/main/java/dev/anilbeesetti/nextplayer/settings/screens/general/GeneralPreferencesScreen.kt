package dev.anilbeesetti.nextplayer.settings.screens.general

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.ClickablePreferenceItem
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.rememberTvListFocusRequester
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusDown
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GeneralPreferencesScreen(viewModel: GeneralPreferencesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GeneralPreferencesScreenContent(
        state = state,
        onAction = viewModel::onAction,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun GeneralPreferencesScreenContent(
    state: GeneralPreferencesUiState,
    onAction: (GeneralPreferencesUiEvent) -> Unit,
) {
    var includeNetworkConnections by rememberSaveable { mutableStateOf(false) }
    val exportDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            onAction(
                GeneralPreferencesUiEvent.ExportSettings(
                    it,
                    includeNetworkConnections,
                ),
            )
        }
    }
    val importDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { onAction(GeneralPreferencesUiEvent.PrepareImport(it)) }
        }
    val listFocusRequester = rememberTvListFocusRequester()
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.general_name),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = {
                            onAction(GeneralPreferencesUiEvent.NavigateUp)
                        },
                        modifier = Modifier.tvFocusDown(target = listFocusRequester),
                    ) {
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
                .tvListFocus(focusRequester = listFocusRequester)
                .padding(paddingValues = innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (state.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            ListSectionTitle(text = stringResource(id = R.string.user_data))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    title = stringResource(R.string.settings_export),
                    description = stringResource(R.string.settings_export_description),
                    icon = NextIcons.ArrowUpward,
                    enabled = !state.isBusy,
                    onClick = {
                        includeNetworkConnections = false
                        onAction(
                            GeneralPreferencesUiEvent.ShowDialog(
                                GeneralPreferencesDialog.ExportSettingsDialog,
                            ),
                        )
                    },
                    isFirstItem = true,
                )
                ClickablePreferenceItem(
                    title = stringResource(R.string.settings_import),
                    description = stringResource(R.string.settings_import_description),
                    icon = NextIcons.ArrowDownward,
                    enabled = !state.isBusy,
                    onClick = {
                        importDocument.launch(
                            arrayOf(
                                "application/json",
                                "text/plain",
                                "application/octet-stream",
                            ),
                        )
                    },
                )
                ClickablePreferenceItem(
                    title = stringResource(R.string.delete_thumbnail_cache),
                    description = stringResource(R.string.delete_thumbnail_cache_description),
                    icon = NextIcons.DeleteSweep,
                    enabled = !state.isBusy,
                    onClick = {
                        onAction(
                            GeneralPreferencesUiEvent.ShowDialog(
                                value = GeneralPreferencesDialog.ClearThumbnailCacheDialog,
                            ),
                        )
                    },
                )
                ClickablePreferenceItem(
                    title = stringResource(R.string.reset_settings),
                    description = stringResource(R.string.reset_settings_description),
                    icon = NextIcons.History,
                    enabled = !state.isBusy,
                    onClick = {
                        onAction(
                            GeneralPreferencesUiEvent.ShowDialog(
                                value = GeneralPreferencesDialog.ResetSettingsDialog,
                            ),
                        )
                    },
                    isLastItem = true,
                )
            }
        }

        state.showDialog?.let { dialog ->
            when (dialog) {
                GeneralPreferencesDialog.ExportSettingsDialog -> ExportSettingsDialog(
                    includeNetworkConnections = includeNetworkConnections,
                    onIncludeNetworkConnectionsChanged = { includeNetworkConnections = it },
                    onDismiss = { onAction(GeneralPreferencesUiEvent.ShowDialog(null)) },
                    onConfirm = {
                        onAction(GeneralPreferencesUiEvent.ShowDialog(null))
                        val timestamp = SimpleDateFormat(
                            "yyyyMMdd-HHmmss-SSS",
                            Locale.ROOT,
                        ).format(Date())
                        exportDocument.launch("nextplayer-settings-$timestamp.json")
                    },
                )

                GeneralPreferencesDialog.ImportSettingsDialog -> ImportSettingsDialog(
                    onConfirm = { onAction(GeneralPreferencesUiEvent.ImportSettings) },
                    onDismiss = { onAction(GeneralPreferencesUiEvent.ShowDialog(null)) },
                )

                GeneralPreferencesDialog.ClearThumbnailCacheDialog -> {
                    NextDialog(
                        onDismissRequest = {
                            onAction(GeneralPreferencesUiEvent.ShowDialog(value = null))
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.delete_thumbnail_cache),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onAction(GeneralPreferencesUiEvent.ClearThumbnailCache)
                                    onAction(GeneralPreferencesUiEvent.ShowDialog(value = null))
                                },
                            ) { Text(text = stringResource(R.string.delete)) }
                        },
                        dismissButton = {
                            CancelButton(onClick = {
                                onAction(GeneralPreferencesUiEvent.ShowDialog(value = null))
                            })
                        },
                        content = {
                            Text(
                                text = stringResource(R.string.delete_thumbnail_cache_confirmation),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                    )
                }

                GeneralPreferencesDialog.ResetSettingsDialog -> {
                    NextDialog(
                        onDismissRequest = {
                            onAction(GeneralPreferencesUiEvent.ShowDialog(value = null))
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.reset_settings),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onAction(GeneralPreferencesUiEvent.ResetSettings)
                                    onAction(GeneralPreferencesUiEvent.ShowDialog(value = null))
                                },
                            ) { Text(text = stringResource(R.string.reset)) }
                        },
                        dismissButton = {
                            CancelButton(onClick = {
                                onAction(GeneralPreferencesUiEvent.ShowDialog(value = null))
                            })
                        },
                        content = {
                            Text(
                                text = stringResource(R.string.reset_settings_confirmation),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                    )
                }
            }
        }
    }
}
