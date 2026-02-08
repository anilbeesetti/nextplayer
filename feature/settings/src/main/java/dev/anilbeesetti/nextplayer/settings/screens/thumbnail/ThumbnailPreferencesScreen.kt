package dev.anilbeesetti.nextplayer.settings.screens.thumbnail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
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
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.ThumbnailGenerationStrategy
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSlider
import dev.anilbeesetti.nextplayer.core.ui.components.SingleSelectablePreference
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme

@Composable
fun ThumbnailPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: ThumbnailPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ThumbnailPreferencesContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThumbnailPreferencesContent(
    uiState: ThumbnailPreferencesUiState,
    onNavigateUp: () -> Unit,
    onEvent: (ThumbnailPreferencesEvent) -> Unit,
) {
    val preferences = uiState.preferences

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.thumbnail_generation),
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
            ListSectionTitle(text = stringResource(id = R.string.thumbnail_generation_strategy))

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                val totalRows = 3
                SingleSelectablePreference(
                    title = stringResource(id = R.string.first_frame),
                    description = stringResource(id = R.string.first_frame_desc),
                    selected = preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FIRST_FRAME,
                    onClick = {
                        onEvent(ThumbnailPreferencesEvent.UpdateStrategy(ThumbnailGenerationStrategy.FIRST_FRAME))
                    },
                    index = 0,
                    count = totalRows,
                )
                SingleSelectablePreference(
                    title = stringResource(id = R.string.frame_at_position),
                    description = stringResource(id = R.string.frame_at_position_desc),
                    selected = preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE,
                    onClick = {
                        onEvent(ThumbnailPreferencesEvent.UpdateStrategy(ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE))
                    },
                    index = 1,
                    count = totalRows,
                )
                SingleSelectablePreference(
                    title = stringResource(id = R.string.hybrid),
                    description = stringResource(id = R.string.hybrid_desc),
                    selected = preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.HYBRID,
                    onClick = {
                        onEvent(ThumbnailPreferencesEvent.UpdateStrategy(ThumbnailGenerationStrategy.HYBRID))
                    },
                    index = 2,
                    count = totalRows,
                )
            }

            PreferenceSlider(
                enabled = preferences.thumbnailGenerationStrategy != ThumbnailGenerationStrategy.FIRST_FRAME,
                modifier = Modifier.padding(vertical = 16.dp),
                title = stringResource(R.string.frame_position),
                description = stringResource(R.string.frame_position_value, preferences.thumbnailFramePosition * 100),
                icon = NextIcons.Frame,
                value = uiState.preferences.thumbnailFramePosition * 100,
                valueRange = 0f..100f,
                onValueChange = {
                    onEvent(
                        ThumbnailPreferencesEvent.UpdateFramePosition(it / 100f),
                    )
                },
                trailingContent = {
                    FilledIconButton(
                        enabled = preferences.thumbnailGenerationStrategy != ThumbnailGenerationStrategy.FIRST_FRAME,
                        onClick = {
                            onEvent(
                                ThumbnailPreferencesEvent.UpdateFramePosition(
                                    ApplicationPreferences.DEFAULT_THUMBNAIL_FRAME_POSITION,
                                ),
                            )
                        },
                    ) {
                        Icon(
                            imageVector = NextIcons.History,
                            contentDescription = stringResource(id = R.string.reset_seek_sentivity),
                        )
                    }
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ThumbnailPreferencesScreenPreview() {
    NextPlayerTheme {
        ThumbnailPreferencesContent(
            uiState = ThumbnailPreferencesUiState(),
            onNavigateUp = {},
            onEvent = {},
        )
    }
}
