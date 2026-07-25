package dev.anilbeesetti.nextplayer.feature.network.screens.addconnection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.components.tvFocusRing
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.network.ObserveAsEvents

/**
 * Default path per protocol: SMB expects a bare share name (no leading slash), while FTP and WebDAV
 * are rooted at "/".
 */
private fun defaultPathFor(protocol: NetworkProtocol): String =
    if (protocol == NetworkProtocol.SMB) "" else "/"

private fun normalizedPathFor(protocol: NetworkProtocol, path: String): String {
    val trimmed = path.trim()
    if (protocol == NetworkProtocol.SMB) return trimmed
    return trimmed.trim('/').let { if (it.isEmpty()) "/" else "/$it" }
}

@Composable
fun AddConnectionScreenRoute(
    onNavigateUp: () -> Unit,
    viewModel: AddConnectionViewModel,
) {
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val existing by viewModel.existingConnection.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.savedEvents) { onNavigateUp() }

    AddConnectionScreen(
        isEdit = viewModel.isEdit,
        existing = existing,
        saveState = saveState,
        onNavigateUp = onNavigateUp,
        onFieldChanged = viewModel::clearError,
        onTestAndSave = viewModel::testAndSave,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddConnectionScreen(
    isEdit: Boolean,
    existing: NetworkConnection?,
    saveState: SaveState,
    onNavigateUp: () -> Unit,
    onFieldChanged: () -> Unit,
    onTestAndSave: (NetworkConnection) -> Unit,
) {
    var protocol by rememberSaveable { mutableStateOf(NetworkProtocol.SMB) }
    var name by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    var path by rememberSaveable { mutableStateOf(defaultPathFor(NetworkProtocol.SMB)) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var useHttps by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // Prefill when editing an existing connection (fires once it loads).
    LaunchedEffect(existing) {
        existing?.let {
            protocol = it.protocol
            name = it.name
            host = it.host
            port = it.port?.toString() ?: ""
            path = normalizedPathFor(it.protocol, it.path)
            username = it.username
            password = it.password
            useHttps = it.useHttps
        }
    }

    val isTesting = saveState is SaveState.Testing
    val canSave = name.isNotBlank() && host.isNotBlank() && !isTesting

    fun submit() {
        onTestAndSave(
            NetworkConnection(
                name = name.trim(),
                protocol = protocol,
                host = host.trim(),
                port = port.toIntOrNull(),
                path = normalizedPathFor(protocol, path),
                username = username.trim(),
                password = password,
                useHttps = protocol == NetworkProtocol.WEBDAV && useHttps,
            ),
        )
    }

    val onChange: (() -> Unit) -> Unit = { setter -> setter(); onFieldChanged() }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(if (isEdit) R.string.edit_connection else R.string.add_connection),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp, modifier = Modifier.tvFocusRing()) {
                        Icon(
                            imageVector = NextIcons.Close,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.protocol),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val protocols = NetworkProtocol.entries
                protocols.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = protocol == entry,
                        onClick = {
                            onChange {
                                // Keep the path in sync with the protocol's default unless the user
                                // has customized it (SMB wants a bare share name, others a "/" root).
                                if (path == defaultPathFor(protocol)) path = defaultPathFor(entry)
                                protocol = entry
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, protocols.size),
                        modifier = Modifier.tvFocusRing(shape = SegmentedButtonDefaults.itemShape(index, protocols.size)),
                    ) {
                        Text(entry.name)
                    }
                }
            }

            val moveToNext = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

            OutlinedTextField(
                value = name,
                onValueChange = { onChange { name = it } },
                label = { Text(stringResource(R.string.connection_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = moveToNext,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = host,
                onValueChange = { onChange { host = it } },
                label = { Text(stringResource(R.string.host)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = moveToNext,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { new -> onChange { port = new.filter { it.isDigit() } } },
                label = { Text(stringResource(R.string.port_hint, protocol.defaultPort)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = moveToNext,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = path,
                onValueChange = { onChange { path = it } },
                label = { Text(stringResource(R.string.path_share)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = moveToNext,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { onChange { username = it } },
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = moveToNext,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { onChange { password = it } },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (canSave) submit()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (protocol == NetworkProtocol.WEBDAV) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.use_https), modifier = Modifier.weight(1f))
                    Switch(checked = useHttps, onCheckedChange = { onChange { useHttps = it } })
                }
            }

            (saveState as? SaveState.Error)?.let {
                Text(
                    text = it.message ?: stringResource(R.string.connection_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.size(4.dp))
            Button(
                onClick = { submit() },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusRing(shape = MaterialTheme.shapes.large),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.connecting))
                } else {
                    Text(stringResource(R.string.test_and_save))
                }
            }
        }
    }
}
