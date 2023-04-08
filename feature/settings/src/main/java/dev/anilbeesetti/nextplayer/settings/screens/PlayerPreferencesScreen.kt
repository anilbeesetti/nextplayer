package dev.anilbeesetti.nextplayer.settings.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.feature.settings.R
import dev.anilbeesetti.nextplayer.settings.composables.ClickablePreferenceItem

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
                        text = stringResource(id = R.string.player)
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
            }
            if (uiState.showResumeDialog) {
                AlertDialog(
                    title = {
                        Text(text = stringResource(id = R.string.resume))
                    },
                    dismissButton = {
                        Button(
                            onClick = { viewModel.onEvent(PlayerPreferencesEvent.ResumeDialog(false)) },
                            modifier = Modifier
                                .widthIn(min = 72.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(text = stringResource(id = R.string.resume))
                        }
                    },
                    onDismissRequest = { viewModel.onEvent(PlayerPreferencesEvent.ResumeDialog(false)) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.updateResume() },
                            modifier = Modifier
                                .widthIn(min = 72.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(text = stringResource(id = R.string.resume))
                        }
                    },
                )
            }
        }
    }
}