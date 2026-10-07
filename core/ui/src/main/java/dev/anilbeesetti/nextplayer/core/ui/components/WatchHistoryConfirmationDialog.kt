package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.R

@Composable
fun WatchHistoryConfirmationDialog(
    enableHistory: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(if (enableHistory) R.string.turn_on_watch_history else R.string.turn_off_watch_history)
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        content = {
            Text(stringResource(if (enableHistory) R.string.turn_on_watch_history_confirmation else R.string.turn_off_watch_history_confirmation))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(title) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
