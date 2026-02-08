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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.ThumbnailGenerationStrategy
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSlider
import dev.anilbeesetti.nextplayer.core.ui.components.SingleSelectablePreference
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import kotlin.math.abs

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
    var frameSliderValue by rememberSaveable { mutableFloatStateOf(preferences.thumbnailFramePosition * 100f) }
    var pendingChange by remember { mutableStateOf<ThumbnailPreferenceChange?>(null) }

    LaunchedEffect(preferences.thumbnailFramePosition, pendingChange) {
        if (pendingChange == null) {
            frameSliderValue = preferences.thumbnailFramePosition * 100f
        }
    }

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
                SingleSelectablePreference(
                    title = stringResource(id = R.string.first_frame),
                    description = stringResource(id = R.string.first_frame_desc),
                    selected = preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FIRST_FRAME,
                    onClick = {
                        if (preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FIRST_FRAME) return@SingleSelectablePreference
                        pendingChange = ThumbnailPreferenceChange.Strategy(ThumbnailGenerationStrategy.FIRST_FRAME)
                    },
                    isFirstItem = true
                )
                SingleSelectablePreference(
                    title = stringResource(id = R.string.frame_at_position),
                    description = stringResource(id = R.string.frame_at_position_desc),
                    selected = preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE,
                    onClick = {
                        if (preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE) return@SingleSelectablePreference
                        pendingChange = ThumbnailPreferenceChange.Strategy(ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE)
                    },
                )
                SingleSelectablePreference(
                    title = stringResource(id = R.string.hybrid),
                    description = stringResource(id = R.string.hybrid_desc),
                    selected = preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.HYBRID,
                    onClick = {
                        if (preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.HYBRID) return@SingleSelectablePreference
                        pendingChange = ThumbnailPreferenceChange.Strategy(ThumbnailGenerationStrategy.HYBRID)
                    },
                    isLastItem = true
                )
            }

            PreferenceSlider(
                enabled = preferences.thumbnailGenerationStrategy != ThumbnailGenerationStrategy.FIRST_FRAME,
                modifier = Modifier.padding(vertical = 16.dp),
                title = stringResource(R.string.frame_position),
                description = stringResource(R.string.frame_position_value, frameSliderValue),
                icon = NextIcons.Frame,
                value = frameSliderValue,
                valueRange = 0f..100f,
                isFirstItem = true,
                isLastItem = true,
                onValueChange = { frameSliderValue = it },
                onValueChangeFinished = {
                    val newPosition = frameSliderValue / 100f
                    if (abs(newPosition - preferences.thumbnailFramePosition) > 0.0001f) {
                        pendingChange = ThumbnailPreferenceChange.FramePosition(newPosition)
                    }
                },
                trailingContent = {
                    FilledIconButton(
                        enabled = preferences.thumbnailGenerationStrategy != ThumbnailGenerationStrategy.FIRST_FRAME,
                        onClick = {
                            val defaultPosition = ApplicationPreferences.DEFAULT_THUMBNAIL_FRAME_POSITION
                            if (abs(defaultPosition - preferences.thumbnailFramePosition) > 0.0001f) {
                                frameSliderValue = defaultPosition * 100f
                                pendingChange = ThumbnailPreferenceChange.FramePosition(defaultPosition)
                            }
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

        pendingChange?.let { change ->
            NextDialog(
                onDismissRequest = {
                    pendingChange = null
                    frameSliderValue = preferences.thumbnailFramePosition * 100f
                },
                title = { Text(text = stringResource(id = R.string.thumbnail_generation)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (change) {
                                is ThumbnailPreferenceChange.Strategy -> {
                                    onEvent(ThumbnailPreferencesEvent.UpdateStrategy(change.strategy))
                                }
                                is ThumbnailPreferenceChange.FramePosition -> {
                                    onEvent(ThumbnailPreferencesEvent.UpdateFramePosition(change.position))
                                }
                            }
                            pendingChange = null
                        },
                    ) {
                        Text(text = stringResource(id = R.string.okay))
                    }
                },
                dismissButton = {
                    CancelButton(
                        onClick = {
                            pendingChange = null
                            frameSliderValue = preferences.thumbnailFramePosition * 100f
                        },
                    )
                },
                content = {
                    Text(
                        text = stringResource(id = R.string.thumbnail_setting_change_confirmation),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )
        }
    }
}

private sealed interface ThumbnailPreferenceChange {
    data class Strategy(val strategy: ThumbnailGenerationStrategy) : ThumbnailPreferenceChange
    data class FramePosition(val position: Float) : ThumbnailPreferenceChange
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
