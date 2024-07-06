package dev.anilbeesetti.nextplayer.core.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.R

@Composable
fun PermissionMissingView(
    isGranted: Boolean,
    showRationale: Boolean,
    permission: String,
    launchPermissionRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isGranted) {
        content()
    } else if (showRationale) {
        PermissionRationaleDialog(
            text = stringResource(
                id = R.string.permission_info,
                permission,
            ),
            onConfirmButtonClick = launchPermissionRequest,
        )
    } else {
        PermissionDetailView(
            text = stringResource(
                id = R.string.permission_settings,
                permission,
            ),
        )
    }
}
