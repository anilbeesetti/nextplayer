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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ThemeConfig
import dev.anilbeesetti.nextplayer.core.ui.api.AndroidPermissionState
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerFolderScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerNavigationRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToMediaPickerFolderScreen
import dev.anilbeesetti.nextplayer.navigation.settingsNavGraph
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var synchronizer: MediaSynchronizer

    private val viewModel: MainActivityViewModel by viewModels()

    private val storagePermission = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_VIDEO
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Manifest.permission.READ_EXTERNAL_STORAGE
        else -> Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

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

        installSplashScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NextPlayerTheme(
                darkTheme = shouldUseDarkTheme(uiState = uiState),
                highContrastDarkTheme = shouldUseHighContrastDarkTheme(uiState = uiState),
                dynamicColor = shouldUseDynamicTheming(uiState = uiState)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    val storagePermissionState = rememberPermissionState(permission = storagePermission)
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

                    LaunchedEffect(key1 = storagePermissionState.status.isGranted) {
                        if (storagePermissionState.status.isGranted) {
                            synchronizer.startSync()
                        }
                    }

                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = mediaPickerNavigationRoute
                    ) {
                        mediaPickerScreen(
                            permissionState = storagePermissionState.toAndroidPermissionState(),
                            onPlayVideo = this@MainActivity::startPlayerActivity,
                            onFolderClick = navController::navigateToMediaPickerFolderScreen,
                            onSettingsClick = navController::navigateToSettings
                        )
                        mediaPickerFolderScreen(
                            onNavigateUp = navController::navigateUp,
                            onVideoClick = this@MainActivity::startPlayerActivity
                        )
                        settingsNavGraph(navController = navController)
                    }
                }
            }
        }
    }
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
        ThemeConfig.OFF -> false
        ThemeConfig.ON -> true
    }
}

@Composable
fun shouldUseHighContrastDarkTheme(
    uiState: MainActivityUiState
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> false
    is MainActivityUiState.Success -> uiState.preferences.useHighContrastDarkTheme
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

fun Context.startPlayerActivity(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri, this, PlayerActivity::class.java)
    startActivity(intent)
}

@OptIn(ExperimentalPermissionsApi::class)
fun PermissionState.toAndroidPermissionState() = AndroidPermissionState(
    permission = permission,
    isGranted = status.isGranted,
    shouldShowRationale = status.shouldShowRationale,
    grantPermission = this::launchPermissionRequest
)