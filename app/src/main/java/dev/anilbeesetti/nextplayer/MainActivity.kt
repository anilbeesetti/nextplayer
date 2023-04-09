package dev.anilbeesetti.nextplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.composables.PermissionDetailView
import dev.anilbeesetti.nextplayer.composables.PermissionRationaleDialog
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.videoPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.videoPickerScreenRoute
import dev.anilbeesetti.nextplayer.settings.Setting
import dev.anilbeesetti.nextplayer.settings.navigation.aboutPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAboutPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToPlayerPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings
import dev.anilbeesetti.nextplayer.settings.navigation.playerPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.settingsScreen

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
                    color = MaterialTheme.colorScheme.surface
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

                    val navController = rememberNavController()

                    if (storagePermissionState.status.isGranted) {
                        NavHost(
                            navController = navController,
                            startDestination = videoPickerScreenRoute
                        ) {
                            videoPickerScreen(
                                title = getString(R.string.app_name),
                                onVideoItemClick = this@MainActivity::startPlayerActivity,
                                onSettingsClick = navController::navigateToSettings
                            )
                            settingsScreen(
                                onNavigateUp = navController::popBackStack,
                                onItemClick = { setting ->
                                    when (setting) {
                                        Setting.PLAYER -> navController.navigateToPlayerPreferences()
                                        Setting.ABOUT -> navController.navigateToAboutPreferences()
                                        else -> {}
                                    }
                                }
                            )
                            playerPreferencesScreen(
                                onNavigateUp = navController::popBackStack
                            )
                            aboutPreferencesScreen(
                                onNavigateUp = navController::popBackStack
                            )
                        }
                    } else {
                        PermissionScreen(
                            permission = storagePermissionState.permission,
                            permissionStatus = storagePermissionState.status,
                            onGrantPermissionClick = { storagePermissionState.launchPermissionRequest() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
fun PermissionScreen(
    permission: String,
    permissionStatus: PermissionStatus,
    onGrantPermissionClick: () -> Unit
) {
    Column {
        NextCenterAlignedTopAppBar(title = stringResource(id = R.string.app_name))
        if (permissionStatus.shouldShowRationale) {
            PermissionRationaleDialog(
                text = stringResource(id = R.string.permission_info, permission),
                onConfirmButtonClick = onGrantPermissionClick
            )
        } else {
            PermissionDetailView(
                text = stringResource(id = R.string.permission_settings, permission)
            )
        }
    }
}

fun Context.startPlayerActivity(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri, this, PlayerActivity::class.java)
    startActivity(intent)
}
