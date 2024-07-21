package dev.anilbeesetti.nextplayer.feature.videopicker.composables.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog

@Composable
fun LoadingDialogComponent(message: String? = null) {
    NextDialog(
        onDismissRequest = {},
        title = {},
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Text(
                    text = message ?: stringResource(id = R.string.loading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {},
    )
}