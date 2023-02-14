package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import dev.anilbeesetti.nextplayer.feature.videopicker.R

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRationaleDialog(
    permissionState: PermissionState,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.permission_request),
            )
        },
        text = {
            Text(text = stringResource(id = R.string.permission_info))
        },
        confirmButton = {
            Button(onClick = { permissionState.launchPermissionRequest() }) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    )
}