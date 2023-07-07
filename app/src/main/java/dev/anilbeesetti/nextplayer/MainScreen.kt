package dev.anilbeesetti.nextplayer

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import dev.anilbeesetti.nextplayer.composables.PermissionDetailView
import dev.anilbeesetti.nextplayer.composables.PermissionRationaleDialog
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextCenterAlignedTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.navigation.MEDIA_ROUTE
import dev.anilbeesetti.nextplayer.navigation.mediaNavGraph
import dev.anilbeesetti.nextplayer.navigation.startPlayerActivity
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings


const val MAIN_ROUTE = "main_screen_route"

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    permissionState: PermissionState,
    mainNavController: NavHostController,
    mediaNavController: NavHostController
) {
    val context = LocalContext.current
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { it?.let(context::startPlayerActivity) }
    )

    Scaffold(
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = { selectVideoFileLauncher.launch("video/*") }
                ) {
                    Icon(
                        imageVector = NextIcons.FileOpen,
                        contentDescription = stringResource(id = R.string.play_file)
                    )
                }
                FloatingActionButton(
                    onClick = { showUrlDialog = true }
                ) {
                    Icon(
                        imageVector = NextIcons.Link,
                        contentDescription = stringResource(id = R.string.play_url)
                    )
                }
            }
        }
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
            Column {
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
        if (showUrlDialog) {
            NetworkUrlDialog(
                onDismiss = { showUrlDialog = false },
                onDone = { context.startPlayerActivity(Uri.parse(it)) }
            )
        }
    }
}


@Composable
fun NetworkUrlDialog(
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var url by rememberSaveable { mutableStateOf("") }
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_stream)) },
        content = {
            Text(text = stringResource(R.string.enter_a_network_url))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.example_url)) }
            )
        },
        confirmButton = { DoneButton(onClick = { if (url.isBlank()) onDismiss() else onDone(url) }) },
        dismissButton = { CancelButton(onClick = onDismiss) }
    )
}