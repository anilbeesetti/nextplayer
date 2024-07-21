package dev.anilbeesetti.nextplayer.feature.videopicker.composables.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.remotesubs.service.Subtitle
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog

@Composable
fun SubtitleResultDialogComponent(
    modifier: Modifier = Modifier,
    data: List<Subtitle>,
    onDismissRequest: () -> Unit,
    onSubtitleSelected: (Subtitle) -> Unit,
) {
    var selectedData: Subtitle? by rememberSaveable { mutableStateOf(null) }

    NextDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Subtitles", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        content = {
            LazyColumn(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data) { subtitle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = selectedData == subtitle,
                                onValueChange = { selectedData = subtitle },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RadioButton(
                            selected = selectedData == subtitle,
                            onClick = null,
                        )
                        Column {
                            Text(
                                text = subtitle.name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = buildString {
                                    append(subtitle.languageName)
                                    if (subtitle.rating != null) {
                                        append(", ${subtitle.rating}")
                                    }
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedData?.let { onSubtitleSelected(it) } },
                enabled = selectedData != null,
            ) {
                Text(text = stringResource(id = R.string.download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
    )
}