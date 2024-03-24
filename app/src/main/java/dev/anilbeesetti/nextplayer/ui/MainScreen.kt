package dev.anilbeesetti.nextplayer.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.navigation.MEDIA_ROUTE
import dev.anilbeesetti.nextplayer.navigation.mediaNavGraph
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings

const val MAIN_ROUTE = "main_screen_route"

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun MainScreen(
    permissionState: PermissionState,
    mainNavController: NavHostController,
    mediaNavController: NavHostController
) {
    val context = LocalContext.current

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .consumeWindowInsets(it)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            if (permissionState.status.isGranted) {
                NavHost(
                    navController = mediaNavController,
                    startDestination = MEDIA_ROUTE
                ) {
                    mediaNavGraph(
                        context = context,
                        mainNavController = mainNavController,
                        mediaNavController = mediaNavController
                    )
                }
            } else {
                NextCenterAlignedTopAppBar(
                    title = stringResource(id = R.string.app_name),
                    navigationIcon = {
                        IconButton(onClick = mainNavController::navigateToSettings) {
                            Icon(
                                imageVector = NextIcons.Settings,
                                contentDescription = stringResource(id = R.string.settings)
                            )
                        }
                    }
                )
                if (permissionState.status.shouldShowRationale) {
                    PermissionRationaleDialog(
                        text = stringResource(
                            id = R.string.permission_info,
                            permissionState.permission
                        ),
                        onConfirmButtonClick = permissionState::launchPermissionRequest
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
        }
    }
}
