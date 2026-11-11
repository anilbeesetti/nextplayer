package dev.anilbeesetti.nextplayer.feature.player.state

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.readSubtitleAsUtf8
import dev.anilbeesetti.nextplayer.feature.player.extensions.getSubtitleMime
import dev.anilbeesetti.nextplayer.feature.player.model.SecondarySubtitle
import java.nio.charset.Charset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val POLL_INTERVAL_MS = 100L
private const val MICROS_PER_MILLI = 1_000L

/**
 * The cues of the [subtitle] file that are showing at the player's current position.
 *
 * The file is parsed by this app instead of by the player, because the player renders a single
 * text track at a time. The cues are empty while another media item than the one the subtitle was
 * picked for is playing.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberSecondaryCues(
    player: Player?,
    subtitle: SecondarySubtitle?,
    subtitleEncoding: String,
): List<Cue> {
    val context = LocalContext.current
    val timedCues by produceState(
        initialValue = emptyList<CuesWithTiming>(),
        key1 = subtitle,
        key2 = subtitleEncoding,
    ) {
        value = emptyList()
        value = subtitle?.let {
            context.parseSubtitle(
                uri = it.uri,
                encoding = subtitleEncoding,
            )
        }.orEmpty()
    }
    var activeCues by remember { mutableStateOf(emptyList<Cue>()) }

    LaunchedEffect(
        player,
        subtitle,
        timedCues,
    ) {
        if (player == null || subtitle == null || timedCues.isEmpty()) {
            activeCues = emptyList()
            return@LaunchedEffect
        }
        while (isActive) {
            activeCues = if (player.currentMediaItem?.mediaId == subtitle.mediaId) {
                timedCues.activeCuesAt(positionUs = player.currentPosition * MICROS_PER_MILLI)
            } else {
                emptyList()
            }
            delay(POLL_INTERVAL_MS)
        }
    }
    return activeCues
}

@OptIn(UnstableApi::class)
internal fun List<CuesWithTiming>.activeCuesAt(positionUs: Long): List<Cue> {
    val activeItems = filter {
        it.startTimeUs <= positionUs &&
            (it.durationUs == C.TIME_UNSET || positionUs < it.startTimeUs + it.durationUs)
    }
    return activeItems.flatMap { it.cues }
}

@OptIn(UnstableApi::class)
private suspend fun Context.parseSubtitle(uri: Uri, encoding: String): List<CuesWithTiming> {
    return withContext(Dispatchers.Default) {
        try {
            val charset = encoding.takeIf { it.isNotEmpty() && Charset.isSupported(it) }
                ?.let { Charset.forName(it) }
            val bytes = readSubtitleAsUtf8(
                uri = uri,
                charset = charset,
            ) ?: return@withContext emptyList()
            val format = Format.Builder()
                .setSampleMimeType(Uri.parse(getFilenameFromUri(uri)).getSubtitleMime())
                .build()
            val parser = DefaultSubtitleParserFactory().create(format)
            buildList {
                parser.parse(
                    bytes,
                    SubtitleParser.OutputOptions.allCues(),
                ) { add(it) }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(
                "SecondaryCues",
                "Unable to read the secondary subtitle",
                exception,
            )
            emptyList()
        }
    }
}
