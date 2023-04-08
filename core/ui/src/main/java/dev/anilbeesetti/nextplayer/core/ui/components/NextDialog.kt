package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.anilbeesetti.nextplayer.core.ui.R

@Composable
fun NextDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        content = {
            Surface(
                modifier = modifier
                    .padding(NextDialogDefaults.dialogMargin),
                shape = NextDialogDefaults.shape,
                tonalElevation = NextDialogDefaults.tonalElevation,
                color = NextDialogDefaults.containerColor
            ) {
                Column(
                    modifier = Modifier
                        .sizeIn(minWidth = MinWidth, maxWidth = MaxWidth)
                ) {
                    CompositionLocalProvider(LocalContentColor provides NextDialogDefaults.titleContentColor) {
                        ProvideTextStyle(value = MaterialTheme.typography.headlineSmall) {
                            Box(
                                modifier = Modifier
                                    .padding(
                                        start = NextDialogDefaults.dialogPadding,
                                        top = NextDialogDefaults.dialogPadding,
                                        end = NextDialogDefaults.dialogPadding,
                                        bottom = NextDialogDefaults.spaceBy
                                    )
                            ) {
                                title()
                            }
                        }
                    }
                    CompositionLocalProvider(LocalContentColor provides NextDialogDefaults.textContentColor) {
                        ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                            Box(
                                modifier = Modifier
                                    .weight(weight = 1f, fill = false)
                                    .align(Alignment.Start)
                            ) {
                                content()
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = NextDialogDefaults.dialogPadding,
                                top = NextDialogDefaults.spaceBy,
                                end = NextDialogDefaults.dialogPadding,
                                bottom = NextDialogDefaults.dialogPadding
                            ),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        confirmButton()
                        Spacer(modifier = Modifier.width(8.dp))
                        dismissButton()
                    }
                }
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}

object NextDialogDefaults {
    val shape: Shape @Composable get() = AlertDialogDefaults.shape
    val containerColor: Color @Composable get() = AlertDialogDefaults.containerColor
    val titleContentColor: Color @Composable get() = AlertDialogDefaults.titleContentColor
    val textContentColor: Color @Composable get() = AlertDialogDefaults.textContentColor
    val tonalElevation: Dp = AlertDialogDefaults.TonalElevation
    val dialogPadding: Dp = 24.dp
    val dialogMargin: Dp = 12.dp
    val spaceBy: Dp = 16.dp
}

private val MinWidth = 280.dp
private val MaxWidth = 560.dp

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