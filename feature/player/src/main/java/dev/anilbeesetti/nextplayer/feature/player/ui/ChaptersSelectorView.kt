package dev.anilbeesetti.nextplayer.feature.player.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.Chapter
import dev.anilbeesetti.nextplayer.core.model.ChapterListConverter
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.state.ChaptersState

@Composable
fun BoxScope.ChaptersSelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    chaptersState: ChaptersState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val chapters = chaptersState.chapters

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val imported = text?.let { ChapterListConverter.fromStringToList(it) }.orEmpty()
        if (imported.isEmpty()) {
            Toast.makeText(context, R.string.invalid_chapters_file, Toast.LENGTH_SHORT).show()
        } else {
            chaptersState.importChapters(imported)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(ChapterListConverter.fromListToString(chapters).toByteArray())
            }
        }
        Toast.makeText(context, R.string.chapters_exported, Toast.LENGTH_SHORT).show()
    }

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.chapters),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = { chaptersState.addBookmarkAtCurrentPosition() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.add_bookmark),
                )
            }
            IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_chapters),
                    contentDescription = stringResource(R.string.import_chapters),
                )
            }
            IconButton(
                onClick = { if (chapters.isNotEmpty()) exportLauncher.launch("chapters.json") },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_export),
                    contentDescription = stringResource(R.string.export_chapters),
                )
            }
        }

        if (chapters.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.no_chapters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = chapters,
                    key = { "${it.timestampMs}:${it.title}" },
                ) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        onClick = {
                            chaptersState.seekTo(chapter.timestampMs)
                            onDismiss()
                        },
                        onDelete = { chaptersState.removeChapter(chapter) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = Utils.formatDurationMillis(chapter.timestampMs),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
