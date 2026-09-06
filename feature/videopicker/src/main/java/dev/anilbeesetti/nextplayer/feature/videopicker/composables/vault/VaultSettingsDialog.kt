package dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSwitch

@Composable
internal fun VaultSettingsDialog(
    biometricEnabled: Boolean,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = rememberVaultBiometricAvailable()
    var authenticating by rememberSaveable { mutableStateOf(false) }

    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_settings)) },
        content = {
            PreferenceSwitch(
                title = stringResource(R.string.unlock_with_biometrics),
                description = stringResource(
                    if (available || biometricEnabled) R.string.vault_biometric_description else R.string.vault_biometric_unavailable,
                ),
                isChecked = biometricEnabled,
                enabled = !authenticating && (biometricEnabled || available),
                isFirstItem = true,
                isLastItem = true,
                onClick = {
                    if (biometricEnabled) onBiometricEnabledChange(false) else authenticating = true
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
    )

    if (authenticating) {
        VaultBiometricPrompt(
            title = stringResource(R.string.enable_biometric_unlock),
            negativeButtonText = stringResource(R.string.cancel),
            onResult = { authenticated ->
                authenticating = false
                if (authenticated) onBiometricEnabledChange(true)
            },
        )
    }
}
