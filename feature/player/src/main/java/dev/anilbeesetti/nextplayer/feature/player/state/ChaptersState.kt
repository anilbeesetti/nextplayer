package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.model.Chapter
import dev.anilbeesetti.nextplayer.feature.player.PlayerViewModel
import kotlinx.coroutines.flow.flowOf

/**
 * Holds the chapter/bookmark list for the currently playing item and the actions that
 * mutate it. Chapters are persisted per-video through [PlayerViewModel.updateChapters] and
 * observed live via [PlayerViewModel.chaptersFlow], re-keyed whenever the media item changes.
 */
@Composable
fun rememberChaptersState(player: Player, viewModel: PlayerViewModel): ChaptersState {
    var mediaId by remember { mutableStateOf(player.currentMediaItem?.mediaId) }
    LaunchedEffect(player) {
        player.listen { events ->
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                mediaId = player.currentMediaItem?.mediaId
            }
        }
    }
    val chapters by remember(mediaId) {
        mediaId?.let(viewModel::chaptersFlow) ?: flowOf(emptyList<Chapter>())
    }.collectAsState(initial = emptyList())

    return ChaptersState(
        chapters = chapters,
        uri = mediaId,
        player = player,
        onUpdate = viewModel::updateChapters,
    )
}

class ChaptersState(
    val chapters: List<Chapter>,
    private val uri: String?,
    private val player: Player,
    private val onUpdate: (uri: String, chapters: List<Chapter>) -> Unit,
) {
    fun seekTo(timestampMs: Long) {
        player.seekTo(timestampMs)
    }

    fun addBookmarkAtCurrentPosition() {
        val u = uri ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        val bookmark = Chapter(title = Utils.formatDurationMillis(position), timestampMs = position)
        onUpdate(u, (chapters + bookmark).sortedBy { it.timestampMs })
    }

    fun importChapters(imported: List<Chapter>) {
        val u = uri ?: return
        if (imported.isEmpty()) return
        val merged = (chapters + imported)
            .distinctBy { it.timestampMs to it.title }
            .sortedBy { it.timestampMs }
        onUpdate(u, merged)
    }

    fun removeChapter(chapter: Chapter) {
        val u = uri ?: return
        onUpdate(u, chapters - chapter)
    }
}
