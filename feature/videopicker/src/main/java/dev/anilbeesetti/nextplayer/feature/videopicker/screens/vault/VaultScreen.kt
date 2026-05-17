package dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun VaultRoute(
    onNavigateUp: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VaultScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onPlayVideo = onPlayVideo,
        onSetPin = viewModel::setPin,
        onVerifyPin = viewModel::verifyPin,
        onVaultUnlocked = viewModel::onVaultUnlocked,
        onPinError = viewModel::onPinError,
        onClearPinError = viewModel::clearPinError,
        onUnhideVideos = viewModel::unhideVideos,
        onClearPin = viewModel::clearPin,
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun VaultScreen(
    uiState: VaultUiState,
    onNavigateUp: () -> Unit = {},
    onPlayVideo: (Uri) -> Unit = {},
    onSetPin: (String) -> Unit = {},
    onVerifyPin: suspend (String) -> Boolean = { false },
    onVaultUnlocked: () -> Unit = {},
    onPinError: () -> Unit = {},
    onClearPinError: () -> Unit = {},
    onUnhideVideos: (List<String>) -> Unit = {},
    onClearPin: () -> Unit = {},
) {
    val selectedFiles = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = "Vault",
                fontWeight = FontWeight.Bold,
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = "Navigate up",
                        )
                    }
                },
                actions = {
                    if (uiState.isUnlocked && uiState.hasPinSet) {
                        TextButton(onClick = onClearPin) {
                            Text("Remove PIN")
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { scaffoldPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding()),
        ) {
            AnimatedContent(
                targetState = uiState.isUnlocked,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 4 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 4 })
                },
                label = "vault_content",
            ) { unlocked ->
                if (!unlocked) {
                    PinGateScreen(
                        hasPinSet = uiState.hasPinSet,
                        pinError = uiState.pinError,
                        onSetPin = onSetPin,
                        onVerifyPin = onVerifyPin,
                        onUnlocked = onVaultUnlocked,
                        onPinError = onPinError,
                        onClearError = onClearPinError,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )
                } else {
                    VaultContentScreen(
                        files = uiState.vaultFiles,
                        selectedFiles = selectedFiles,
                        isUnhiding = uiState.isUnhiding,
                        onPlayVideo = onPlayVideo,
                        onUnhideSelected = {
                            onUnhideVideos(selectedFiles.toList())
                            selectedFiles.clear()
                        },
                        scaffoldPadding = scaffoldPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
    }
}

@Composable
private fun PinGateScreen(
    hasPinSet: Boolean,
    pinError: Boolean,
    onSetPin: (String) -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onPinError: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var phase by rememberSaveable {
        mutableStateOf(if (hasPinSet) PinPhase.ENTER else PinPhase.CREATE)
    }
    var firstPin by rememberSaveable { mutableStateOf("") }
    var enteredPin by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(hasPinSet) {
        if (!hasPinSet && phase == PinPhase.ENTER) phase = PinPhase.CREATE
    }

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = NextIcons.HideSource,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = when (phase) {
                PinPhase.ENTER -> "Enter your vault PIN"
                PinPhase.CREATE -> "Create a vault PIN"
                PinPhase.CONFIRM -> "Confirm your PIN"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = when (phase) {
                PinPhase.ENTER -> "Your PIN protects hidden videos"
                PinPhase.CREATE -> "Choose a 4-digit PIN to protect your vault"
                PinPhase.CONFIRM -> "Re-enter your PIN to confirm"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        PinDotRow(length = enteredPin.length)

        if (pinError) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (phase == PinPhase.CONFIRM) "PINs don't match, try again" else "Incorrect PIN",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.height(32.dp))

        PinNumpad(
            onDigit = { digit ->
                if (enteredPin.length < 4) {
                    enteredPin += digit
                    onClearError()
                    if (enteredPin.length == 4) {
                        scope.launch {
                            when (phase) {
                                PinPhase.ENTER -> {
                                    val ok = onVerifyPin(enteredPin)
                                    if (ok) {
                                        onUnlocked()
                                    } else {
                                        onPinError()
                                        enteredPin = ""
                                    }
                                }
                                PinPhase.CREATE -> {
                                    firstPin = enteredPin
                                    enteredPin = ""
                                    phase = PinPhase.CONFIRM
                                }
                                PinPhase.CONFIRM -> {
                                    if (enteredPin == firstPin) {
                                        onSetPin(enteredPin)
                                        onUnlocked()
                                    } else {
                                        onPinError()
                                        enteredPin = ""
                                        firstPin = ""
                                        phase = PinPhase.CREATE
                                    }
                                }
                            }
                        }
                    }
                }
            },
            onDelete = {
                if (enteredPin.isNotEmpty()) {
                    enteredPin = enteredPin.dropLast(1)
                    onClearError()
                }
            },
        )
    }
}

private enum class PinPhase { ENTER, CREATE, CONFIRM }

@Composable
private fun PinDotRow(length: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(4) { index ->
            val filled = index < length
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    ),
            )
        }
    }
}

@Composable
private fun PinNumpad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )

    Column(modifier = modifier.widthIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (label.isNotEmpty()) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent,
                            )
                            .then(
                                if (label.isNotEmpty()) {
                                    Modifier.clickable {
                                        if (label == "⌫") onDelete() else onDigit(label)
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VaultContentScreen(
    files: List<String>,
    selectedFiles: MutableList<String>,
    isUnhiding: Boolean,
    onPlayVideo: (Uri) -> Unit,
    onUnhideSelected: () -> Unit,
    scaffoldPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (files.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = NextIcons.FolderOff,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Vault is empty",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Hidden videos will appear here.\nLong-press a video and tap Hide.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() + if (selectedFiles.isNotEmpty()) 88.dp else 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it }) { filename ->
                    val selected = filename in selectedFiles
                    VaultFileItem(
                        filename = filename,
                        selected = selected,
                        inSelectionMode = selectedFiles.isNotEmpty(),
                        onClick = { fileUri ->
                            if (selectedFiles.isNotEmpty()) {
                                if (selected) selectedFiles.remove(filename)
                                else selectedFiles.add(filename)
                            } else {
                                onPlayVideo(fileUri)
                            }
                        },
                        onLongClick = {
                            if (selected) selectedFiles.remove(filename)
                            else selectedFiles.add(filename)
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedFiles.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${selectedFiles.size} selected",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { selectedFiles.clear() }) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onUnhideSelected,
                        enabled = !isUnhiding,
                    ) {
                        if (isUnhiding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Unhide")
                    }
                }
            }
        }
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun VaultFileItem(
    filename: String,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClick: (Uri) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vaultDir = remember { context.getExternalFilesDir(null)?.let { File(it, ".vault") } }
    val filePath = remember(filename) { vaultDir?.let { File(it, filename).absolutePath } }
    val fileUri = remember(filePath) { filePath?.let { Uri.fromFile(File(it)) } }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium,
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = { fileUri?.let { onClick(it) } },
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = NextIcons.Video,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxSize(0.5f),
                )
                if (filePath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(filePath)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (!inSelectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = NextIcons.Play,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filename.substringBeforeLast("."),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = filename.substringAfterLast(".").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (selected) {
                Icon(
                    imageVector = NextIcons.CheckBox,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = NextIcons.CheckBoxOutline,
                    contentDescription = "Not selected",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
