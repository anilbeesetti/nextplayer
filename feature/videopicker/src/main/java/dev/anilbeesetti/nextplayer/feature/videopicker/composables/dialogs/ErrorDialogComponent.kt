package dev.anilbeesetti.nextplayer.feature.videopicker.composables.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog

@Composable
fun ErrorDialogComponent(
    errorMessage: String? = null,
    onDismissRequest: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(id = R.string.error))
        },
        content = {
            Text(
                text = errorMessage ?: stringResource(id = R.string.unknown_error_try_again),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
    )
}