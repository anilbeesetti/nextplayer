package dev.anilbeesetti.nextplayer.feature.player.cast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.media3.cast.MediaRouteButton
import androidx.media3.cast.rememberMediaRouteButtonState
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dev.anilbeesetti.nextplayer.core.ui.R
import kotlinx.coroutines.flow.drop

@UnstableApi
@Composable
internal fun CastButton(
    contentColor: Color = Color.White,
    onPickerVisibleChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val available = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
            ConnectionResult.SUCCESS
    }
    if (!available) return
    var granted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        if (granted) {
            // The picker closes with the button, so a parent that hides its controls must wait.
            val state = rememberMediaRouteButtonState()
            val visibleChanged by rememberUpdatedState(onPickerVisibleChange)
            LaunchedEffect(state) {
                snapshotFlow { state.isPickerVisible }.drop(1).collect { visibleChanged(it) }
            }
            MediaRouteButton(state = state)
        } else {
            IconButton(onClick = { launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) {
                Icon(
                    Icons.Outlined.Cast,
                    contentDescription = stringResource(R.string.cast_to_device),
                )
            }
        }
    }
}
