package dev.anilbeesetti.nextplayer.feature.videopicker.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.anilbeesetti.nextplayer.core.ui.api.AndroidPermissionState
import dev.anilbeesetti.nextplayer.core.ui.views.PermissionRationaleDialog
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.views.PermissionDetailView

@Composable
fun PermissionView(
    permissionState: AndroidPermissionState
) {
    if (permissionState.shouldShowRationale) {
        PermissionRationaleDialog(
            text = stringResource(
                id = R.string.permission_info,
                permissionState.permission
            ),
            onConfirmButtonClick = permissionState.grantPermission
        )
    } else {
        PermissionDetailView(
            text = stringResource(
                id = R.string.permission_settings,
                permissionState.permission
            )
        )
    }
}