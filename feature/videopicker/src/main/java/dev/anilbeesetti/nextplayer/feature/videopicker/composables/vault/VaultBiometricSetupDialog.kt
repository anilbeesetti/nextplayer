package dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog

@Composable
internal fun VaultBiometricSetupDialog(onComplete: (Boolean) -> Unit) {
    if (!rememberVaultBiometricAvailable()) {
        LaunchedEffect(Unit) { onComplete(false) }
        return
    }

    var authenticating by rememberSaveable { mutableStateOf(false) }
    NextDialog(
        onDismissRequest = { onComplete(false) },
        title = { Text(stringResource(R.string.vault_biometric_setup_title)) },
        content = { Text(stringResource(R.string.vault_biometric_setup_description)) },
        confirmButton = {
            TextButton(onClick = { authenticating = true }, enabled = !authenticating) {
                Text(stringResource(R.string.enable_biometric_unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = { onComplete(false) }) {
                Text(stringResource(R.string.not_now))
            }
        },
    )

    if (authenticating) {
        VaultBiometricPrompt(
            title = stringResource(R.string.enable_biometric_unlock),
            negativeButtonText = stringResource(R.string.cancel),
            onResult = { authenticated ->
                authenticating = false
                if (authenticated) onComplete(true)
            },
        )
    }
}
