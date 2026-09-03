package com.graviton.feature.player.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.graviton.core.model.MediaChapter
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons
import com.graviton.feature.player.extensions.formatted
import com.graviton.feature.player.ui.OverlayView
import kotlin.time.Duration.Companion.milliseconds

/**
 * Chapters read from the file's chapter sidecar.
 *
 * The chapter that contains the current position is highlighted with the M3 selected-list styling
 * and is also labelled in text, so the state is never conveyed by colour alone. A file with no
 * chapter description shows the empty state - no chapters are ever synthesised.
 */
@Composable
fun BoxScope.ChaptersSheet(
    modifier: Modifier = Modifier,
    show: Boolean,
    chapters: List<MediaChapter>,
    currentPositionMs: Long,
    onJumpTo: (positionMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val currentIndex = chapters.indexOfLast { it.startMs <= currentPositionMs }

    LaunchedEffect(show, currentIndex) {
        if (show && currentIndex in chapters.indices) {
            lazyListState.scrollToItem(currentIndex)
        }
    }

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.chapters),
        onDismiss = onDismiss,
    ) {
        if (chapters.isEmpty()) {
            SheetEmptyState(
                icon = NextIcons.Chapter,
                title = stringResource(R.string.no_chapters_title),
                description = stringResource(R.string.no_chapters_description),
            )
            return@OverlayView
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .padding(bottom = 16.dp),
        ) {
            itemsIndexed(
                items = chapters,
                key = { index, chapter -> "$index-${chapter.startMs}" },
            ) { index, chapter ->
                val isCurrent = index == currentIndex
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) { onJumpTo(chapter.startMs) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isCurrent) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                        headlineColor = if (isCurrent) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                    leadingContent = {
                        Icon(
                            imageVector = if (isCurrent) NextIcons.Play else NextIcons.Chapter,
                            contentDescription = null,
                            tint = if (isCurrent) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    headlineContent = {
                        Text(
                            text = chapter.title.ifBlank { "${index + 1}" },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    // The current chapter states itself in words as well as in colour.
                    supportingContent = if (isCurrent) {
                        { Text(text = stringResource(R.string.current_chapter)) }
                    } else {
                        null
                    },
                    trailingContent = {
                        Text(
                            text = chapter.startMs.milliseconds.formatted(),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
