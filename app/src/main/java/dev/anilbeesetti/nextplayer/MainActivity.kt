package dev.anilbeesetti.nextplayer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.composables.PermissionDetailView
import dev.anilbeesetti.nextplayer.composables.PermissionRationaleDialog
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.videopicker.VideoPickerScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NextPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val storagePermissionState = rememberPermissionState(
                        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_VIDEO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                    )

                    val lifecycleOwner = LocalLifecycleOwner.current

                    DisposableEffect(key1 = lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_START) {
                                storagePermissionState.launchPermissionRequest()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    HomeScreen(
                        permission = storagePermissionState.permission,
                        permissionStatus = storagePermissionState.status,
                        onGrantPermissionClick = { storagePermissionState.launchPermissionRequest() }
                    )
                }
            }
        }
    }
}


@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
private fun HomeScreen(
    permission: String,
    permissionStatus: PermissionStatus,
    onGrantPermissionClick: () -> Unit
) {

    val context = LocalContext.current

    Column {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontWeight = FontWeight.Bold
                )
            }
        )
        when {
            permissionStatus.isGranted -> {
                VideoPickerScreen(
                    onVideoItemClick = { uri ->
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            uri,
                            context,
                            PlayerActivity::class.java
                        )
                        context.startActivity(intent)
                    }
                )
            }
            permissionStatus.shouldShowRationale -> {
                PermissionRationaleDialog(
                    text = stringResource(id = R.string.permission_info, permission),
                    onConfirmButtonClick = onGrantPermissionClick
                )
            }
            permissionStatus.isPermanentlyDeniedOrPermissionIsNotRequested -> {
                PermissionDetailView(
                    text = stringResource(id = R.string.permission_settings, permission)
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
val PermissionStatus.isPermanentlyDeniedOrPermissionIsNotRequested: Boolean
    get() = !this.shouldShowRationale && !this.isGranted
