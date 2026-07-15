package dev.anilbeesetti.nextplayer.feature.playlist.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.playlist.R
import java.net.URI

@Composable
fun PlaylistNameDialog(
    title: String,
    isSaving: Boolean,
    error: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    chosenDocument: String? = null,
    onClearError: () -> Unit = {},
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }
    val trimmedName = name.trim()

    NextDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onClearError()
                    },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = error != null || (attemptedSubmit && trimmedName.isEmpty()),
                    supportingText = {
                        when {
                            error != null -> Text(error)
                            attemptedSubmit && trimmedName.isEmpty() -> Text(stringResource(R.string.name_required))
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                chosenDocument?.let {
                    Text(stringResource(R.string.chosen_document, it))
                }
                if (isSaving) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.creating_playlist))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attemptedSubmit = true
                    if (trimmedName.isNotEmpty()) onConfirm(trimmedName)
                },
                enabled = !isSaving && trimmedName.isNotEmpty(),
                modifier = Modifier.tvFocusRing(),
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isSaving,
                modifier = Modifier.tvFocusRing(),
            ) { Text(stringResource(R.string.cancel)) }
        },
        modifier = modifier,
    )
}

@Composable
fun AddM3uUrlDialog(
    isSaving: Boolean,
    error: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
    onClearError: () -> Unit = {},
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }
    val trimmedName = name.trim()
    val trimmedUrl = url.trim()
    val validUrl = isValidM3uUrl(trimmedUrl)

    NextDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.add_m3u_url_playlist)) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onClearError()
                    },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = attemptedSubmit && trimmedName.isEmpty(),
                    supportingText = {
                        if (attemptedSubmit && trimmedName.isEmpty()) Text(stringResource(R.string.name_required))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        onClearError()
                    },
                    label = { Text(stringResource(R.string.playlist_url)) },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = attemptedSubmit && !validUrl,
                    supportingText = {
                        if (attemptedSubmit && !validUrl) {
                            Text(
                                stringResource(
                                    if (trimmedUrl.isEmpty()) R.string.url_required else R.string.invalid_url,
                                ),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it) }
                if (isSaving) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.creating_playlist))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attemptedSubmit = true
                    if (trimmedName.isNotEmpty() && validUrl) onConfirm(trimmedName, trimmedUrl)
                },
                enabled = !isSaving && trimmedName.isNotEmpty() && validUrl,
                modifier = Modifier.tvFocusRing(),
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isSaving,
                modifier = Modifier.tvFocusRing(),
            ) { Text(stringResource(R.string.cancel)) }
        },
        modifier = modifier,
    )
}

@Composable
fun PlaylistTargetDialog(
    playlists: List<PlaylistSummary>,
    isSaving: Boolean,
    error: String?,
    onDismissRequest: () -> Unit,
    onPlaylistSelected: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClearError: () -> Unit = {},
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    if (showCreateDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.create_new_playlist),
            isSaving = isSaving,
            error = error,
            onDismissRequest = { showCreateDialog = false },
            onConfirm = onCreatePlaylist,
            onClearError = onClearError,
        )
        return
    }

    NextDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.choose_playlist)) },
        content = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                if (playlists.isEmpty()) {
                    item { Text(stringResource(R.string.no_editable_playlists)) }
                }
                items(playlists, key = { it.id }) { playlist ->
                    TextButton(
                        onClick = { onPlaylistSelected(playlist.id) },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusRing(),
                    ) { Text(playlist.name) }
                }
                item {
                    TextButton(
                        onClick = { showCreateDialog = true },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusRing(),
                    ) { Text(stringResource(R.string.create_new_playlist)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest, modifier = Modifier.tvFocusRing()) {
                Text(stringResource(R.string.cancel))
            }
        },
        modifier = modifier,
    )
}

internal fun isValidM3uUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
        !uri.host.isNullOrBlank()
}.getOrDefault(false)
