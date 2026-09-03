package com.graviton.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons

/**
 * Opens a network stream in the running player session.
 *
 * The URL is handed to the same `MediaController` that is already playing, so the session, the
 * surface and the service all stay exactly as they are.
 */
@Composable
fun NetworkStreamDialog(
    onPlay: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = NextIcons.Network, contentDescription = null) },
        title = { Text(text = stringResource(R.string.network_stream)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.enter_a_network_url))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text(text = stringResource(R.string.example_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onPlay(url.trim()) },
            ) {
                Text(text = stringResource(R.string.play))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}
