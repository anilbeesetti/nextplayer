package com.graviton.feature.music.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.graviton.core.common.Utils
import com.graviton.core.model.AudioTrack
import com.graviton.core.model.MusicPlaylist
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons

/**
 * Picks an existing MediaStore playlist for a song, or creates a new one.
 *
 * MediaStore playlists are the only playlist store the music library reads, so this dialog writes
 * to the same place the Playlists tab lists from — no parallel storage is introduced.
 */
@Composable
fun AddToPlaylistDialog(
    playlists: List<MusicPlaylist>,
    onSelect: (MusicPlaylist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(NextIcons.PlaylistAdd, contentDescription = null) },
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_PLAYLIST_NAME) name = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            ListItem(
                                modifier = Modifier.clickable { onSelect(playlist) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = {
                                    Icon(NextIcons.Playlist, contentDescription = null)
                                },
                                headlineContent = {
                                    Text(playlist.name.ifBlank { stringResource(R.string.untitled_playlist) })
                                },
                                supportingContent = {
                                    Text(
                                        pluralStringResource(
                                            R.plurals.song_count,
                                            playlist.trackCount,
                                            playlist.trackCount,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    TextButton(onClick = { creating = true }) {
                        Text(stringResource(R.string.new_playlist))
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onCreate(name.trim()) },
                ) {
                    Text(stringResource(R.string.create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Shows the metadata the library already holds for a song. No values are invented. */
@Composable
fun TrackInformationDialog(track: AudioTrack, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(NextIcons.Info, contentDescription = null) },
        title = { Text(track.displayTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(stringResource(R.string.artist), track.displayArtist)
                InfoRow(stringResource(R.string.album), track.displayAlbum)
                InfoRow(
                    label = stringResource(R.string.duration),
                    value = formatTrackDuration(track.duration),
                    monospace = true,
                )
                InfoRow(
                    label = stringResource(R.string.size),
                    value = Utils.formatFileSize(track.size),
                    monospace = true,
                )
                InfoRow(stringResource(R.string.path), track.path.ifBlank { stringResource(R.string.unknown) })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.okay)) }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            modifier = Modifier.weight(0.6f),
        )
    }
}

private const val MAX_PLAYLIST_NAME = 60
