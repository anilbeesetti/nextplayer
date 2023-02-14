package dev.anilbeesetti.nextplayer.feature.videopicker

import android.Manifest
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.PermissionDetailView
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.PermissionRationaleDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItemsPickerView

@Composable
fun VideoPicker(
    viewModel: VideoPickerViewModel = hiltViewModel(),
    onVideoItemClick: (uri: Uri) -> Unit
) {
    val uiState by viewModel.videoPickerUiState.collectAsState()

    VideoPickerScreen(
        uiState = uiState,
        onVideoItemClick = onVideoItemClick,
        onResumeEvent = viewModel::scanMedia
    )

}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
internal fun VideoPickerScreen(
    uiState: VideoPickerUiState,
    onVideoItemClick: (uri: Uri) -> Unit,
    onResumeEvent: () -> Unit
) {

    val readExternalStoragePermissionState = rememberPermissionState(
        permission = Manifest.permission.READ_EXTERNAL_STORAGE
    )

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(key1 = lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResumeEvent()
            }
            if (event == Lifecycle.Event.ON_START) {
                readExternalStoragePermissionState.launchPermissionRequest()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            readExternalStoragePermissionState.status.isGranted -> {
                VideoItemsPickerView(
                    videoItems = uiState.videoItems,
                    onVideoItemClick = onVideoItemClick
                )
            }
            readExternalStoragePermissionState.status.shouldShowRationale -> {
                PermissionRationaleDialog(
                    permissionState = readExternalStoragePermissionState
                )
            }
            readExternalStoragePermissionState.status.isPermanentlyDeniedOrPermissionIsNotRequested -> {
                PermissionDetailView()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
val PermissionStatus.isPermanentlyDeniedOrPermissionIsNotRequested: Boolean
    get() = !this.shouldShowRationale && !this.isGranted
