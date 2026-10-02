package dev.anilbeesetti.nextplayer.feature.network

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dev.anilbeesetti.nextplayer.core.ui.composables.PermissionMissingView

/** Gate connection screens before their ViewModels can start local network requests. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun LocalNetworkPermission(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT < 37) {
        content()
        return
    }

    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_LOCAL_NETWORK)
    PermissionMissingView(
        isGranted = permissionState.status.isGranted,
        showRationale = permissionState.status.shouldShowRationale,
        permission = permissionState.permission,
        launchPermissionRequest = permissionState::launchPermissionRequest,
        content = content,
    )
}
