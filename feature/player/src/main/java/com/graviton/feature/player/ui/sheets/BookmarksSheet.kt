package com.graviton.feature.player.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.graviton.core.model.VideoBookmark
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.player.extensions.formatted
import com.graviton.feature.player.ui.OverlayView
import kotlin.time.Duration.Companion.milliseconds

/**
 * Bookmarks for the item being played: add at the current position, jump back, or delete.
 *
 * The list is backed by the application preferences store, so a bookmark survives closing the
 * player and reopening the same file.
 */
@Composable
fun BoxScope.BookmarksSheet(
    modifier: Modifier = Modifier,
    show: Boolean,
    bookmarks: List<VideoBookmark>,
    currentPositionMs: Long,
    onAddBookmark: (label: String) -> Unit,
    onJumpTo: (positionMs: Long) -> Unit,
    onDelete: (VideoBookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.bookmarks),
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { showAddDialog = true },
                enabled = bookmarks.size < VideoBookmark.MAX_BOOKMARKS_PER_MEDIA,
            ) {
                Icon(imageVector = NextIcons.BookmarkAdd, contentDescription = null)
                Text(
                    text = stringResource(R.string.add_bookmark),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = currentPositionMs.milliseconds.formatted(),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (bookmarks.isEmpty()) {
            SheetEmptyState(
                icon = NextIcons.Bookmark,
                title = stringResource(R.string.no_bookmarks_title),
                description = stringResource(R.string.no_bookmarks_description),
            )
        } else {
            Text(
                text = pluralStringResource(R.plurals.bookmark_count, bookmarks.size, bookmarks.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(bottom = 16.dp),
            ) {
                items(
                    items = bookmarks,
                    key = { "${it.positionMs}-${it.createdAt}" },
                ) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onJumpTo = { onJumpTo(bookmark.positionMs) },
                        onDelete = { onDelete(bookmark) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddBookmarkDialog(
            positionMs = currentPositionMs,
            onConfirm = { label ->
                onAddBookmark(label)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: VideoBookmark,
    onJumpTo: () -> Unit,
    onDelete: () -> Unit,
) {
    val timestamp = bookmark.positionMs.milliseconds.formatted()
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onJumpTo),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = NextIcons.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = {
            Text(
                text = bookmark.label.ifBlank { timestamp },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (bookmark.label.isNotBlank()) {
            {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = NextIcons.Delete,
                    contentDescription = stringResource(R.string.delete_bookmark),
                )
            }
        },
    )
}

@Composable
private fun AddBookmarkDialog(
    positionMs: Long,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    val timestamp = remember(positionMs) { positionMs.milliseconds.formatted() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = NextIcons.BookmarkAdd, contentDescription = null) },
        title = { Text(text = stringResource(R.string.add_bookmark)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(60) },
                    label = { Text(text = stringResource(R.string.bookmark_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label) }) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}
