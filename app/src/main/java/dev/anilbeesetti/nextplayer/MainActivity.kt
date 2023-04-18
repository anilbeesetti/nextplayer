package dev.anilbeesetti.nextplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import dev.anilbeesetti.nextplayer.core.datastore.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.folderVideoPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerScreenRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToFolderVideoPickerScreen
import dev.anilbeesetti.nextplayer.settings.Setting
import dev.anilbeesetti.nextplayer.settings.navigation.aboutPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.appearancePreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAboutPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAppearancePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToPlayerPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings
import dev.anilbeesetti.nextplayer.settings.navigation.playerPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.settingsScreen
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    val viewModel: MainActivityViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NextPlayerTheme(
                darkTheme = shouldUseDarkTheme(uiState = uiState),
                dynamicColor = shouldUseDynamicTheming(uiState = uiState)
            ) {
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
                            startDestination = mediaPickerScreenRoute
                        ) {
                            mediaPickerScreen(
                                onVideoItemClick = this@MainActivity::startPlayerActivity,
                                onSettingsClick = navController::navigateToSettings,
                                onFolderCLick = navController::navigateToFolderVideoPickerScreen
                            )
                            folderVideoPickerScreen(
                                onNavigateUp = navController::popBackStack,
                                onVideoItemClick = this@MainActivity::startPlayerActivity
                            )
                            settingsScreen(
                                onNavigateUp = navController::popBackStack,
                                onItemClick = { setting ->
                                    when (setting) {
                                        Setting.APPEARANCE -> navController.navigateToAppearancePreferences()
                                        Setting.PLAYER -> navController.navigateToPlayerPreferences()
                                        Setting.ABOUT -> navController.navigateToAboutPreferences()
                                    }
                                }
                            )
                            appearancePreferencesScreen(
                                onNavigateUp = navController::popBackStack
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

/**
 * Returns `true` if dark theme should be used, as a function of the [uiState] and the
 * current system context.
 */
@Composable
private fun shouldUseDarkTheme(
    uiState: MainActivityUiState
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> isSystemInDarkTheme()
    is MainActivityUiState.Success -> when (uiState.preferences.themeConfig) {
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.LIGHT -> false
        ThemeConfig.DARK -> true
    }
}

/**
 * Returns `true` if the dynamic color is disabled, as a function of the [uiState].
 */
@Composable
private fun shouldUseDynamicTheming(
    uiState: MainActivityUiState
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useDynamicColors
}
