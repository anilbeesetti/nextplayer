package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun NextDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dialogProperties: DialogProperties = NextDialogDefaults.dialogProperties
) {

    val configuration = LocalConfiguration.current

    AlertDialog(
        title = title,
        text = {
            Column {
                Divider()
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    content()
                }
                Divider()
            }
        },
        modifier = modifier
            .widthIn(max = configuration.screenWidthDp.dp - NextDialogDefaults.dialogMargin * 2),
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        properties = dialogProperties
    )
}

object NextDialogDefaults {
    val dialogProperties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        decorFitsSystemWindows = true
    )
    val dialogMargin: Dp = 16.dp
    val spaceBy: Dp = 16.dp
}
