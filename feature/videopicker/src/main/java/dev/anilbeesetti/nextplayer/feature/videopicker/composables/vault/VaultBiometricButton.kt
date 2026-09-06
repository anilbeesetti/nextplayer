package dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
internal fun VaultBiometricButton(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? FragmentActivity ?: return
    val available = remember(activity) {
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    if (!available) return

    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)
    var active by remember(activity) { mutableStateOf(false) }
    val prompt = remember(activity) {
        BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (active) currentOnAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (active && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        Toast.makeText(activity, errString, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
    val promptInfo = remember(activity) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.open_vault))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText(activity.getString(R.string.use_vault_pin))
            .build()
    }

    DisposableEffect(prompt) {
        active = true
        onDispose {
            active = false
            // AndroidX retains the prompt and reattaches its callback after rotation.
            if (!activity.isChangingConfigurations) prompt.cancelAuthentication()
        }
    }

    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(prompt) {
        if (!prompted) {
            prompted = true
            prompt.authenticate(promptInfo)
        }
    }

    TextButton(
        modifier = modifier,
        onClick = { prompt.authenticate(promptInfo) },
    ) {
        Icon(imageVector = NextIcons.Fingerprint, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(text = stringResource(R.string.unlock_with_biometrics))
    }
}
