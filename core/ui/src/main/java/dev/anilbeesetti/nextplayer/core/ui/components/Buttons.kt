package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.R

@Composable
fun DoneButton(
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(text = stringResource(R.string.done))
    }
}

@Composable
fun CancelButton(
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(text = stringResource(R.string.cancel))
    }
}