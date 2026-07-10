package dev.anilbeesetti.nextplayer.feature.iptv

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ImportMode { URL, FILE }

@Composable
internal fun ImportPlaylistDialog(
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onImportUrl: (url: String, name: String?) -> Unit,
    onImportContent: (content: String, name: String, source: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(ImportMode.URL) }
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var pickedFileName: String? by remember { mutableStateOf(null) }
    var pickedFileContent: String? by remember { mutableStateOf(null) }
    var pickedFileSource: String? by remember { mutableStateOf(null) }
    var readError by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            if (result.isNullOrBlank()) {
                readError = true
            } else {
                readError = false
                pickedFileContent = result
                pickedFileSource = uri.toString()
                pickedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "playlist.m3u"
                if (name.isBlank()) name = pickedFileName!!.substringBeforeLast('.')
            }
        }
    }

    val confirmEnabled = when (mode) {
        ImportMode.URL -> url.isNotBlank() && !isImporting
        ImportMode.FILE -> pickedFileContent != null && name.isNotBlank() && !isImporting
    }

    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_playlist)) },
        content = {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == ImportMode.URL,
                    onClick = { mode = ImportMode.URL },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(NextIcons.Link, contentDescription = null, modifier = Modifier.width(18.dp)) },
                ) { Text(stringResource(R.string.import_from_url)) }
                SegmentedButton(
                    selected = mode == ImportMode.FILE,
                    onClick = { mode = ImportMode.FILE },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(NextIcons.UploadFile, contentDescription = null, modifier = Modifier.width(18.dp)) },
                ) { Text(stringResource(R.string.upload_m3u_file)) }
            }

            Spacer(Modifier.height(16.dp))

            when (mode) {
                ImportMode.URL -> {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.playlist_url)) },
                        placeholder = { Text(stringResource(R.string.playlist_url_hint)) },
                    )
                }

                ImportMode.FILE -> {
                    OutlinedButton(
                        onClick = { filePicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(NextIcons.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(pickedFileName ?: stringResource(R.string.upload_m3u_file))
                    }
                    if (readError) {
                        Text(
                            text = stringResource(R.string.no_channels_found),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name_optional)) },
            )

            if (isImporting) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.importing))
                }
            }
        },
        confirmButton = {
            DoneButton(
                enabled = confirmEnabled,
                onClick = onClick@{
                    when (mode) {
                        ImportMode.URL -> onImportUrl(url.trim(), name.trim().ifBlank { null })
                        ImportMode.FILE -> {
                            val content = pickedFileContent ?: return@onClick
                            val source = pickedFileSource ?: return@onClick
                            onImportContent(content, name.trim(), source)
                        }
                    }
                },
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@Composable
internal fun QualityPickerDialog(
    item: ChannelListItem,
    onDismiss: () -> Unit,
    onSelect: (dev.anilbeesetti.nextplayer.core.model.IptvChannel) -> Unit,
) {
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_quality)) },
        content = {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.variants.forEach { variant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = false, onClick = { onSelect(variant.channel) })
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(NextIcons.Quality, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = variant.quality.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}
