package dev.anilbeesetti.nextplayer.settings.screens.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing

@Composable
internal fun ExportSettingsDialog(
    includeNetworkConnections: Boolean,
    onIncludeNetworkConnectionsChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_export)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.export_action)) }
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.settings_export_confirmation))
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = includeNetworkConnections,
                        role = Role.Checkbox,
                        onValueChange = onIncludeNetworkConnectionsChanged,
                    ).tvFocusRing(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeNetworkConnections,
                        onCheckedChange = null,
                    )
                    Text(text = stringResource(R.string.settings_export_include_credentials))
                }
                Text(
                    text = stringResource(R.string.settings_export_credentials_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
internal fun ImportSettingsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_import)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.import_action)) }
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
        content = {
            Text(
                text = stringResource(R.string.settings_import_confirmation),
                style = MaterialTheme.typography.titleSmall,
            )
        },
    )
}
