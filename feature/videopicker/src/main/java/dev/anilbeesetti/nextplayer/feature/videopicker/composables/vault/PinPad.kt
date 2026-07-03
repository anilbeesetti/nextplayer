package dev.anilbeesetti.nextplayer.feature.videopicker.composables.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.ui.components.requestFocusUntilLanded
import dev.anilbeesetti.nextplayer.core.ui.components.thenIf
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault.VAULT_PIN_LENGTH

/**
 * Row of dots indicating how many PIN digits have been entered so far, out of [length].
 * Shakes briefly when [error] becomes true (e.g. wrong PIN, mismatched confirmation).
 */
@Composable
fun PinDotsIndicator(
    filledCount: Int,
    error: Boolean,
    modifier: Modifier = Modifier,
    length: Int = VAULT_PIN_LENGTH,
) {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error) {
            for (target in listOf(16f, -16f, 10f, -10f, 0f)) {
                offsetX.animateTo(target, animationSpec = tween(50))
            }
        }
    }

    Row(
        modifier = modifier.offset(x = offsetX.value.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(length) { index ->
            val filled = index < filledCount
            Spacer(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (error) {
                            MaterialTheme.colorScheme.error
                        } else if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
            )
        }
    }
}

/**
 * A simple numeric keypad (0-9, backspace) used to enter a PIN.
 */
@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isTv = remember { context.isTelevision }
    val firstKeyFocusRequester = remember { FocusRequester() }
    var isFirstKeyFocused by remember { mutableStateOf(false) }

    // On a TV, focus the first key when the pad appears so it's usable with a D-pad immediately.
    if (isTv) {
        LaunchedEffect(Unit) {
            firstKeyFocusRequester.requestFocusUntilLanded(attempts = 20) { isFirstKeyFocused }
        }
    }

    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEachIndexed { colIndex, digit ->
                    PinKey(
                        modifier = Modifier
                            .weight(1f)
                            .thenIf(rowIndex == 0 && colIndex == 0) {
                                focusRequester(firstKeyFocusRequester)
                                    .onFocusChanged { isFirstKeyFocused = it.hasFocus }
                            },
                        isTv = isTv,
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            PinKey(
                modifier = Modifier.weight(1f),
                isTv = isTv,
                label = "0",
                onClick = { onDigit('0') },
            )
            PinKeyIcon(
                modifier = Modifier.weight(1f),
                isTv = isTv,
                onClick = onBackspace,
            )
        }
    }
}

@Composable
private fun PinKey(
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .aspectRatio(1.4f)
            .tvFocusRing(isTv, shape = MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PinKeyIcon(
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .aspectRatio(1.4f)
            .tvFocusRing(isTv, shape = MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Backspace,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
