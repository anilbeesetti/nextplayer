package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.feature.player.extensions.formatted
import io.github.anilbeesetti.nextlib.mediainfo.Chapter
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.ChaptersView(
    show: Boolean,
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    onChapterSelected: (Chapter) -> Unit,
) {
    OverlayView(show = show, title = stringResource(R.string.chapters)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            state = rememberLazyListState(initialFirstVisibleItemIndex = currentChapterIndex.coerceAtLeast(0)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            itemsIndexed(chapters, key = { _, chapter -> chapter.start }) { index, chapter ->
                NextSegmentedListItem(
                    selected = index == currentChapterIndex,
                    isFirstItem = index == 0,
                    isLastItem = index == chapters.lastIndex,
                    onClick = { onChapterSelected(chapter) },
                    content = {
                        Text(
                            text = chapter.titleOrDefault(index),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(text = chapter.start.milliseconds.formatted())
                    },
                )
            }
        }
    }
}

@Composable
internal fun Chapter.titleOrDefault(index: Int): String =
    title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chapter_number, index + 1)
