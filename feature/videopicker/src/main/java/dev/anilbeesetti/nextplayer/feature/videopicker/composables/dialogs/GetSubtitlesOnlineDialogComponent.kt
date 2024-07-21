package dev.anilbeesetti.nextplayer.feature.videopicker.composables.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun GetSubtitlesOnlineDialogComponent(
    modifier: Modifier = Modifier,
    video: Video,
    onDismissRequest: () -> Unit,
    onConfirm: (searchText: String?, language: String) -> Unit,
) {
    var searchText by rememberSaveable { mutableStateOf(video.displayName) }
    var language by rememberSaveable { mutableStateOf("en") }

    NextDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Get subtitles online",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            Column {
                Text(text = "Search subtitle from opensubtitles.com")
                Text(text = "Language: English")
                Spacer(modifier = Modifier.size(16.dp))
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(text = "Search") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(searchText, language) }) {
                Text(text = stringResource(id = R.string.okay))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
    )
}