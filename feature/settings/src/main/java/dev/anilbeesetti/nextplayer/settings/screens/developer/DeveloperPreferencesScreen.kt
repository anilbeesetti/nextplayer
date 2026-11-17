package dev.anilbeesetti.nextplayer.settings.screens.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.ArtworkStyle
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.ListSectionTitle
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.SingleSelectablePreference
import dev.anilbeesetti.nextplayer.core.ui.components.playbackScreenInsets
import dev.anilbeesetti.nextplayer.core.ui.components.rememberTvListFocusRequester
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusDown
import dev.anilbeesetti.nextplayer.core.ui.components.tvListFocus
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun DeveloperPreferencesScreen(viewModel: DeveloperPreferencesViewModel) {
    val preferences by viewModel.state.collectAsStateWithLifecycle()
    DeveloperPreferencesScreenContent(
        preferences,
        viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeveloperPreferencesScreenContent(
    preferences: PlayerPreferences,
    onAction: (DeveloperPreferencesEvent) -> Unit,
) {
    val focusRequester = rememberTvListFocusRequester()
    Scaffold(
        modifier = Modifier.testTag("developer_settings"),
        contentWindowInsets = playbackScreenInsets(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            NextTopAppBar(
                title = stringResource(R.string.developer_settings),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = { onAction(DeveloperPreferencesEvent.NavigateUp) },
                        modifier = Modifier.tvFocusDown(focusRequester),
                    ) {
                        Icon(
                            NextIcons.ArrowBack,
                            stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .tvListFocus(focusRequester).padding(padding).padding(horizontal = 16.dp),
        ) {
            ArtworkStylePreference(
                title = stringResource(R.string.controls_artwork_style),
                tag = "controls_artwork_style",
                selected = preferences.controlsArtworkStyle,
                onSelect = { onAction(DeveloperPreferencesEvent.ControlsArtwork(it)) },
            )
            ArtworkStylePreference(
                title = stringResource(R.string.queue_artwork_style),
                tag = "queue_artwork_style",
                selected = preferences.queueArtworkStyle,
                onSelect = { onAction(DeveloperPreferencesEvent.QueueArtwork(it)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtworkStylePreference(
    title: String,
    tag: String,
    selected: ArtworkStyle,
    onSelect: (ArtworkStyle) -> Unit,
) {
    ListSectionTitle(text = title)
    Column(
        Modifier.selectableGroup().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        ArtworkStyle.entries.forEachIndexed { index, style ->
            val titleRes = when (style) {
                ArtworkStyle.AUDIO -> R.string.artwork_audio_style
                ArtworkStyle.VIDEO -> R.string.artwork_video_style
            }
            SingleSelectablePreference(
                title = stringResource(titleRes),
                modifier = Modifier.testTag("${tag}_${style.name}").semantics {
                    this.selected = selected == style
                    role = Role.RadioButton
                },
                selected = selected == style,
                onClick = { onSelect(style) },
                isFirstItem = index == 0,
                isLastItem = index == ArtworkStyle.entries.lastIndex,
            )
        }
    }
}
