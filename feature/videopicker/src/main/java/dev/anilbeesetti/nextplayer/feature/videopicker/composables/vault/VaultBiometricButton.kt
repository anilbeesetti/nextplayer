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
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
internal fun VaultBiometricButton(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!rememberVaultBiometricAvailable()) return

    var showPrompt by rememberSaveable { mutableStateOf(true) }
    if (showPrompt) {
        VaultBiometricPrompt(
            title = stringResource(R.string.open_vault),
            negativeButtonText = stringResource(R.string.use_vault_pin),
            onResult = { authenticated ->
                showPrompt = false
                if (authenticated) onAuthenticated()
            },
        )
    }

    TextButton(
        modifier = modifier,
        onClick = { showPrompt = true },
    ) {
        Icon(imageVector = NextIcons.Fingerprint, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(text = stringResource(R.string.unlock_with_biometrics))
    }
}

@Composable
internal fun rememberVaultBiometricAvailable(): Boolean {
    val activity = LocalActivity.current as? FragmentActivity ?: return false
    val manager = remember(activity) { BiometricManager.from(activity) }
    var available by remember(manager) {
        mutableStateOf(manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS)
    }
    LifecycleResumeEffect(manager) {
        available = manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        onPauseOrDispose { }
    }
    return available
}

@Composable
internal fun VaultBiometricPrompt(
    title: String,
    negativeButtonText: String,
    onResult: (Boolean) -> Unit,
) {
    val activity = LocalActivity.current as? FragmentActivity ?: return

    val currentOnResult by rememberUpdatedState(onResult)
    var active by remember(activity) { mutableStateOf(false) }
    val prompt = remember(activity) {
        BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!active) return
                    active = false
                    currentOnResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!active) return
                    active = false
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        Toast.makeText(activity, errString, Toast.LENGTH_SHORT).show()
                    }
                    currentOnResult(false)
                }
            },
        )
    }
    val promptInfo = remember(title, negativeButtonText) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeButtonText)
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
}
