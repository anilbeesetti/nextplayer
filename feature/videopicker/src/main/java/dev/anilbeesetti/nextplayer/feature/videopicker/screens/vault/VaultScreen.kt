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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import java.util.concurrent.TimeUnit
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
        onDeleteVideos = viewModel::deleteVaultFiles,
        onRequestClearPin = viewModel::requestClearPin,
        onDismissClearPin = viewModel::dismissClearPin,
        onClearPinAfterVerify = viewModel::clearPinAfterVerify,
        onClearClearPinError = viewModel::clearClearPinError,
        onDismissFirstTimeTip = viewModel::dismissFirstTimeTip,
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
    onDeleteVideos: (List<String>) -> Unit = {},
    onRequestClearPin: () -> Unit = {},
    onDismissClearPin: () -> Unit = {},
    onClearPinAfterVerify: (String) -> Unit = {},
    onClearClearPinError: () -> Unit = {},
    onDismissFirstTimeTip: () -> Unit = {},
) {
    val selectedFiles = remember { mutableStateListOf<String>() }

    // First-time tip dialog
    if (uiState.showFirstTimeTip) {
        AlertDialog(
            onDismissRequest = onDismissFirstTimeTip,
            icon = {
                Icon(
                    imageVector = NextIcons.HideSource,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            },
            title = { Text("How to hide videos") },
            text = {
                Text(
                    text = "To hide a video, long-press any video in your library and tap the Hide option.\n\nTo access hidden videos later, long-press the app icon on your home screen and tap \"Vault\".",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                Button(onClick = onDismissFirstTimeTip) {
                    Text("Got it")
                }
            },
        )
    }

    // Verify old PIN before removing it
    if (uiState.showVerifyPinToClear) {
        VerifyPinDialog(
            error = uiState.clearPinError,
            onVerify = onClearPinAfterVerify,
            onDismiss = onDismissClearPin,
            onClearError = onClearClearPinError,
        )
    }

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
                        TextButton(onClick = onRequestClearPin) {
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
                        durations = uiState.vaultFileDurations,
                        selectedFiles = selectedFiles,
                        isUnhiding = uiState.isUnhiding,
                        onPlayVideo = onPlayVideo,
                        onUnhideSelected = {
                            onUnhideVideos(selectedFiles.toList())
                            selectedFiles.clear()
                        },
                        onDeleteSelected = {
                            onDeleteVideos(selectedFiles.toList())
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

// ── Verify-old-PIN dialog shown before removing PIN ──────────────────────────

@Composable
private fun VerifyPinDialog(
    error: Boolean,
    onVerify: (String) -> Unit,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var enteredPin by rememberSaveable { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = NextIcons.HideSource,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Enter current PIN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Enter your existing PIN to remove it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < enteredPin.length) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                ),
                        )
                    }
                }
                if (error) {
                    Text(
                        text = "Incorrect PIN",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                PinNumpad(
                    onDigit = { digit ->
                        if (enteredPin.length < 4) {
                            enteredPin += digit
                            onClearError()
                            if (enteredPin.length == 4) {
                                scope.launch {
                                    onVerify(enteredPin)
                                    enteredPin = ""
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
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ── PIN gate (lock screen) ────────────────────────────────────────────────────

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

// ── Vault content (unlocked) ──────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VaultContentScreen(
    files: List<String>,
    durations: Map<String, Long>,
    selectedFiles: MutableList<String>,
    isUnhiding: Boolean,
    onPlayVideo: (Uri) -> Unit,
    onUnhideSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    scaffoldPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf<String?>(null) }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        val count = selectedFiles.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = NextIcons.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(if (count == 1) "Delete video?" else "Delete $count videos?")
            },
            text = {
                Text("This action cannot be undone. The selected video(s) will be permanently deleted from the vault.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteSelected()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Info dialog for a single file
    showInfoDialog?.let { filename ->
        val context = LocalContext.current
        val vaultDir = remember { context.getExternalFilesDir(null)?.let { java.io.File(it, ".vault") } }
        val file = remember(filename) { vaultDir?.let { java.io.File(it, filename) } }
        val fileSizeBytes = remember(filename) { file?.length() ?: 0L }
        val fileSizeText = remember(fileSizeBytes) {
            when {
                fileSizeBytes >= 1_000_000_000L -> "%.2f GB".format(fileSizeBytes / 1_000_000_000.0)
                fileSizeBytes >= 1_000_000L -> "%.2f MB".format(fileSizeBytes / 1_000_000.0)
                fileSizeBytes >= 1_000L -> "%.1f KB".format(fileSizeBytes / 1_000.0)
                else -> "$fileSizeBytes B"
            }
        }
        val durationMs = durations[filename] ?: 0L
        val durationText = remember(durationMs) { formatDuration(durationMs) }

        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            icon = {
                Icon(
                    imageVector = NextIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text("Video Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(label = "Name", value = filename.substringBeforeLast("."))
                    InfoRow(label = "Format", value = filename.substringAfterLast(".").uppercase())
                    if (durationText.isNotEmpty()) InfoRow(label = "Duration", value = durationText)
                    if (fileSizeBytes > 0L) InfoRow(label = "Size", value = fileSizeText)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = null }) { Text("Close") }
            },
        )
    }

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
                    bottom = scaffoldPadding.calculateBottomPadding() + if (selectedFiles.isNotEmpty()) 100.dp else 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it }) { filename ->
                    val selected = filename in selectedFiles
                    VaultFileItem(
                        filename = filename,
                        durationMs = durations[filename] ?: 0L,
                        selected = selected,
                        inSelectionMode = selectedFiles.isNotEmpty(),
                        onClick = { fileUri ->
                            if (selectedFiles.isNotEmpty()) {
                                // In selection mode: tap toggles selection
                                if (selected) selectedFiles.remove(filename)
                                else selectedFiles.add(filename)
                            } else {
                                // Normal mode: tap plays video
                                onPlayVideo(fileUri)
                            }
                        },
                        onLongClick = {
                            // Long press ALWAYS toggles selection (enters multi-select mode)
                            if (selected) selectedFiles.remove(filename)
                            else selectedFiles.add(filename)
                        },
                        onInfoClick = { showInfoDialog = filename },
                    )
                }
            }
        }

        // ── Bottom action bar (shown when items are selected) ──────────────
        AnimatedVisibility(
            visible = selectedFiles.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "${selectedFiles.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Cancel
                    FilledTonalButton(
                        onClick = { selectedFiles.clear() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    // Delete
                    FilledTonalButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = NextIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                    // Unhide
                    Button(
                        onClick = onUnhideSelected,
                        enabled = !isUnhiding,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isUnhiding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Unhide")
                    }
                }
            }
        }

        // "Unhiding…" banner shown during the copy operation
        AnimatedVisibility(
            visible = isUnhiding,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Restoring video…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

// ── Individual vault file row ─────────────────────────────────────────────────

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun VaultFileItem(
    filename: String,
    durationMs: Long,
    selected: Boolean,
    inSelectionMode: Boolean,
    onClick: (Uri) -> Unit,
    onLongClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vaultDir = remember { context.getExternalFilesDir(null)?.let { File(it, ".vault") } }
    val filePath = remember(filename) { vaultDir?.let { File(it, filename).absolutePath } }
    val fileUri = remember(filePath) { filePath?.let { Uri.fromFile(File(it)) } }

    val formattedDuration = remember(durationMs) { formatDuration(durationMs) }

    val selectionBorderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val cardBackground = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { fileUri?.let { onClick(it) } },
                onLongClick = onLongClick,
            ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = selectionBorderColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackground)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Thumbnail with duration chip and selection indicator
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(
                    imageVector = NextIcons.Video,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.5f),
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
                // Selection overlay — scrim + checkmark
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    )
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp),
                    )
                }
                // Duration chip in bottom-end corner
                if (!selected && durationMs > 0L && formattedDuration.isNotEmpty()) {
                    Text(
                        text = formattedDuration,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(vertical = 1.dp, horizontal = 3.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filename.substringBeforeLast("."),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = filename.substringAfterLast(".").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Show Info button when NOT in selection mode
                if (!inSelectionMode) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Info",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onInfoClick() },
                    )
                }
            }

            // Trailing checkmark in selection mode
            if (inSelectionMode) {
                Icon(
                    imageVector = if (selected)
                        Icons.Filled.CheckCircle
                    else
                        Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
