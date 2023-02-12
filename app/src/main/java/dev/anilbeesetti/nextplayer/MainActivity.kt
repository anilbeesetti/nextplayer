package dev.anilbeesetti.nextplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.ui.theme.NextPlayerTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NextPlayerTheme {

                val context = LocalContext.current
                val viewModel = hiltViewModel<MediaPickerViewModel>()
                val mediaPickerUiState by viewModel.mediaPickerUiState.collectAsState()

                val readExternalStoragePermissionState = rememberPermissionState(
                    permission = android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(key1 = lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.scanMedia()
                        }
                        if (event == Lifecycle.Event.ON_START) {
                            readExternalStoragePermissionState.launchPermissionRequest()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                                LazyColumn(
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    items(mediaPickerUiState.videos) { mediaItem ->
                                        MediaItem(
                                            media = mediaItem,
                                            onClick = {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    File(mediaItem.data).toUri(),
                                                    context,
                                                    PlayerActivity::class.java
                                                )
                                                context.startActivity(intent)
                                            }
                                        )
                                    }
                                }
                            }
                            readExternalStoragePermissionState.status.shouldShowRationale -> {
                                AlertDialog(
                                    onDismissRequest = {},
                                    title = {
                                        Text(
                                            text = getString(R.string.permission_request),
                                        )
                                    },
                                    text = {
                                        Text(text = stringResource(id = R.string.permission_info))
                                    },
                                    confirmButton = {
                                        Button(onClick = { readExternalStoragePermissionState.launchPermissionRequest() }) {
                                            Text(getString(R.string.grant_permission))
                                        }
                                    }
                                )
                            }
                            readExternalStoragePermissionState.status.isPermanentlyDeniedOrPermissionIsNotRequested -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.permission_not_granted),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 5.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(id = R.string.permission_settings),
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 5.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:" + context.packageName)
                                                context.startActivity(this)
                                            }
                                        }
                                    ) {
                                        Text(text = getString(R.string.open_settings))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
val PermissionStatus.isPermanentlyDeniedOrPermissionIsNotRequested: Boolean
    get() = !this.shouldShowRationale && !this.isGranted
