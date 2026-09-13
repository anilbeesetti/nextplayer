package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.github.anilbeesetti.nextlib.mediainfo.Chapter
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberChapters(mediaId: String?): List<Chapter> {
    val context = LocalContext.current
    var chapters by remember(mediaId) { mutableStateOf(emptyList<Chapter>()) }
    LaunchedEffect(mediaId) {
        if (mediaId.isNullOrBlank()) return@LaunchedEffect
        chapters = withContext(Dispatchers.IO) {
            runCatching {
                val uri = mediaId.toUri()
                val mediaInfo = when (uri.scheme?.lowercase()) {
                    "http", "https" -> MediaInfoBuilder().from(mediaId).build()
                    "content", "file" -> context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        MediaInfoBuilder().from(it).build()
                    }
                    else -> null
                }
                try {
                    mediaInfo?.chapters.orEmpty()
                } finally {
                    mediaInfo?.release()
                }
            }.getOrDefault(emptyList())
        }
    }
    return chapters
}

internal fun List<Chapter>.forDuration(durationMs: Long): List<Chapter> =
    filter { it.start >= 0 && it.start < durationMs }
        .sortedBy { it.start }
        .distinctBy { it.start }

internal fun List<Chapter>.currentChapterIndex(positionMs: Long): Int =
    indexOfLast { it.start <= positionMs }
