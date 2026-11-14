package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.google.common.io.ByteStreams

/** Only the served Tracks header changes; no movie copy is written. */
@UnstableApi
internal class LocalCastAudio(private val context: Context) {
    data class Track(val number: Long, val format: Format)
    private data class Selection(val index: Int, val patch: MatroskaTrackPatch)
    private val selections = mutableMapOf<Uri, Selection>()

    fun patchFor(uri: Uri): MatroskaTrackPatch? = selections[uri]?.patch
    fun selectedIndex(uri: Uri, tracks: List<Track>): Int =
        selections[uri]?.index?.takeIf { it in tracks.indices } ?: 0

    fun select(uri: Uri, index: Int, patch: MatroskaTrackPatch?) {
        if (patch == null) {
            selections.remove(uri)
        } else {
            selections[uri] = Selection(
                index,
                patch,
            )
        }
    }

    fun inspect(uri: Uri): List<Track> = readTracks(uri)?.audioTracks.orEmpty().map { track ->
        Track(
            track.number,
            Format.Builder()
                .setId("cast-local-audio:$uri:${track.number}")
                .setSampleMimeType(MimeTypes.AUDIO_UNKNOWN)
                .setLabel(track.name)
                .setLanguage(track.language)
                .build(),
        )
    }

    fun prepare(uri: Uri, track: Track): MatroskaTrackPatch =
        checkNotNull(readTracks(uri)) { "Unsupported Matroska header" }.selectAudio(track.number)

    fun clear() = selections.clear()

    private fun readTracks(uri: Uri): MatroskaTracks? =
        checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            MatroskaTracks.parse(
                ByteStreams.limit(
                    input,
                    MatroskaTracks.MAX_HEADER_BYTES.toLong(),
                ).readBytes(),
            )
        }
}
