package dev.anilbeesetti.nextplayer.feature.videopicker.screens.webdav

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.WebDavServer
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import java.util.UUID

@Composable
fun AddServerDialog(
    onDismiss: () -> Unit,
    onAddServer: (WebDavServer) -> Unit,
    onTestConnection: (WebDavServer) -> Unit = {},
    testConnectionResult: TestConnectionResult? = null,
    isTestingConnection: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add WebDAV Server") },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Enter your WebDAV server details",
                    style = MaterialTheme.typography.bodyMedium,
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    placeholder = { Text("My WebDAV Server") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://example.com/webdav") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    placeholder = { Text("Optional") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Optional") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None 
                                         else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) NextIcons.CheckBoxOutline 
                                             else NextIcons.CheckBox,
                                contentDescription = if (passwordVisible) "Hide password" 
                                                   else "Show password",
                            )
                        }
                    },
                    singleLine = true,
                )
                
                // Test Connection Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            if (url.isNotBlank()) {
                                val server = WebDavServer(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim().ifBlank { "Test Server" },
                                    url = url.trim(),
                                    username = username.trim(),
                                    password = password,
                                )
                                onTestConnection(server)
                            }
                        },
                        enabled = url.isNotBlank() && !isTestingConnection,
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isTestingConnection) "Testing..." else "Test Connection")
                    }
                }
                
                // Test Result Display
                testConnectionResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.isSuccess) 
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (result.isSuccess) NextIcons.CheckBox else NextIcons.Priority,
                                contentDescription = null,
                                tint = if (result.isSuccess) 
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (result.isSuccess) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            DoneButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    val server = WebDavServer(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        url = url.trim(),
                        username = username.trim(),
                        password = password,
                    )
                    onAddServer(server)
                },
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}
