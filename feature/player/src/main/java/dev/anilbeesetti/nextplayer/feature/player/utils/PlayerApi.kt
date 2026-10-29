package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.extensions.getParcelableUriArray
import dev.anilbeesetti.nextplayer.feature.player.extensions.setExtras
import dev.anilbeesetti.nextplayer.feature.player.model.Subtitle
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode

/** The VIEW intent contract shared by MX Player Free, Pro, and Next Player. */
class PlayerApi(private val intent: Intent) {
    private val extras = intent.extras
    val isApiAccess: Boolean get() = extras?.keySet()?.any { it in API_KEYS } == true
    val hasPosition: Boolean get() = position != null
    val hasTitle: Boolean get() = title != null
    val shouldReturnResult: Boolean get() = boolean(API_RETURN_RESULT) == true
    val position: Int? get() = (value(API_POSITION) as? Int)?.takeIf { it >= 0 }
    val title: String? get() = value(API_TITLE) as? String
    val sticky: Boolean? get() = boolean("sticky")
    val videoVisible: Boolean get() = boolean("video") ?: true
    val suppressErrorMessage: Boolean get() = boolean("suppress_error_message") == true
    val dpadUpDown: Int? get() = (value("keys.dpad_up_down") as? Int)?.takeIf { it in 0..1 }
    val orientation: Int? get() = (value("orientation") as? Int)?.takeIf { it in ORIENTATIONS }
    val videoContentScale: VideoContentScale?
        get() = when (value("video_zoom") as? Int) {
            0 -> VideoContentScale.STRETCH
            1 -> VideoContentScale.BEST_FIT
            2 -> VideoContentScale.HUNDRED_PERCENT
            3 -> VideoContentScale.CROP
            else -> null
        }
    val displayAspectRatio: Float?
        get() {
            val horizontal = value("DAR_horz") as? Float ?: return null
            val vertical = value("DAR_vert") as? Float ?: return null
            return (horizontal / vertical).takeIf {
                horizontal > 0 && vertical > 0 && it.isFinite() && it > 0
            }
        }

    private val decoderMode: DecoderMode?
        get() = when ((value("decode_mode") as? Number)?.toInt()) {
            1, 4 -> DecoderMode.HARDWARE
            2 -> DecoderMode.FFMPEG
            else -> null
        }

    @Suppress("DEPRECATION")
    private fun value(key: String): Any? = extras?.get(key)
    private fun boolean(key: String): Boolean? = value(key) as? Boolean

    fun getSubs(): List<Subtitle> {
        val subs = extras?.getParcelableUriArray(API_SUBS) ?: return emptyList()
        val names = value(API_SUBS_NAME) as? Array<*>
        val filenames = value("subs.filename") as? Array<*>
        val enabled = extras.getParcelableUriArray(API_SUBS_ENABLE).orEmpty()
        return subs.mapIndexedNotNull { index, uri ->
            uri?.let {
                Subtitle(
                    name = names?.getOrNull(index) as? String,
                    uri = uri,
                    isSelected = uri in enabled,
                    filename = filenames?.getOrNull(index) as? String,
                )
            }
        }
    }

    fun getPlaylist(): List<String> {
        val playlist = extras?.getParcelableUriArray(API_PLAYLIST)?.filterNotNull()
            ?.map(Uri::toString).orEmpty()
        if (playlist.isEmpty()) return emptyList()
        val requested = intent.data?.toString()
        return if (requested != null &&
            requested !in playlist
        ) {
            listOf(requested) + playlist
        } else {
            playlist
        }
    }

    fun createMediaItems(
        playlist: List<String>,
        selectedIndex: Int,
        subtitles: List<MediaItem.SubtitleConfiguration>,
    ): List<MediaItem> {
        val suppliedUris = extras?.getParcelableUriArray(API_PLAYLIST).orEmpty()
        val suppliedIndices = suppliedUris.withIndex()
            .groupBy(
                { it.value?.toString() },
                { it.index },
            )
            .mapValues { ArrayDeque(it.value) }
        val names = value("video_list.name") as? Array<*>
        val headers = Bundle().apply {
            val entries = value("headers") as? Array<*> ?: return@apply
            entries.asList().chunked(2).forEach { pair ->
                val name = pair[0] as? String
                val content = pair.getOrNull(1) as? String
                if (!name.isNullOrBlank() && content != null &&
                    name.none { it <= ' ' || it >= '\u007f' || it == ':' } &&
                    content.none { it == '\r' || it == '\n' || it == '\u0000' }
                ) {
                    putString(
                        name,
                        content,
                    )
                }
            }
        }
        return playlist.mapIndexed { index, uri ->
            val suppliedIndex = suppliedIndices[uri]?.removeFirstOrNull() ?: -1
            val itemTitle = if (suppliedIndex >= 0 && names != null) {
                names.getOrNull(suppliedIndex) as? String
            } else {
                title.takeIf { index == selectedIndex }
            }
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(uri)
                .setMimeType(
                    intent.type?.takeIf {
                        index == selectedIndex
                    }?.takeUnless { it.endsWith("/*") },
                )
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(itemTitle).setExtras(
                        positionMs = position?.toLong()?.takeIf { index == selectedIndex },
                        videoDecoderMode = decoderMode,
                    ).build(),
                )
                .setSubtitleConfigurations(subtitles.takeIf { index == selectedIndex }.orEmpty())
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder().setExtras(
                        Bundle().apply {
                            putBundle(
                                API_HEADERS_KEY,
                                headers,
                            )
                            if (index == selectedIndex &&
                                hasPosition
                            ) {
                                putBoolean(
                                    API_POSITION_KEY,
                                    true,
                                )
                            }
                        },
                    ).build(),
                )
                .build().let { item ->
                    if (boolean("video_list_is_explicit") ==
                        true
                    ) {
                        item.asExplicitPlaylistItem()
                    } else {
                        item
                    }
                }
        }
    }

    fun getResult(
        isPlaybackFinished: Boolean,
        duration: Long,
        position: Long,
        uri: Uri? = intent.data,
        usedDecoder: DecoderMode? = null,
    ): Intent = Intent(
        API_RESULT_INTENT,
        uri,
    ).apply {
        putExtra(
            API_END_BY,
            if (isPlaybackFinished) "playback_completion" else "user",
        )
        when (usedDecoder) {
            DecoderMode.HARDWARE -> putExtra(
                "decode_mode",
                1.toByte(),
            )

            DecoderMode.FFMPEG, DecoderMode.SOFTWARE -> putExtra(
                "decode_mode",
                2.toByte(),
            )

            else -> Unit
        }
        if (!isPlaybackFinished) {
            if (duration >=
                0
            ) {
                putExtra(
                    API_DURATION,
                    duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
            }
            if (position >=
                0
            ) {
                putExtra(
                    API_POSITION,
                    position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
            }
        }
    }

    companion object {
        const val API_TITLE = "title"
        const val API_POSITION = "position"
        const val API_DURATION = "duration"
        const val API_RETURN_RESULT = "return_result"
        const val API_END_BY = "end_by"
        const val API_SUBS = "subs"
        const val API_SUBS_ENABLE = "subs.enable"
        const val API_SUBS_NAME = "subs.name"
        const val API_PLAYLIST = "video_list"
        const val API_RESULT_INTENT = "com.mxtech.intent.result.VIEW"

        private val ORIENTATIONS = setOf(
            -1,
            0,
            1,
            6,
            7,
            8,
            9,
            10,
            99999,
        )
        private val API_KEYS = setOf(
            API_TITLE,
            API_POSITION,
            API_RETURN_RESULT,
            API_SUBS,
            API_SUBS_ENABLE,
            API_SUBS_NAME,
            API_PLAYLIST,
            "video_list.name",
            "video_list_is_explicit",
            "decode_mode",
            "video",
            "headers",
            "video_zoom",
            "sticky",
            "orientation",
            "DAR_horz",
            "DAR_vert",
            "keys.dpad_up_down",
            "suppress_error_message",
            "secure_uri",
        )
    }
}

private const val API_HEADERS_KEY = "dev.anilbeesetti.nextplayer.api.HEADERS"
private const val API_POSITION_KEY = "dev.anilbeesetti.nextplayer.api.POSITION"

internal val MediaItem.apiHeaders: Map<String, String>
    get() = requestMetadata.extras?.getBundle(API_HEADERS_KEY)?.let { headers ->
        headers.keySet().associateWith { headers.getString(it).orEmpty() }
    }.orEmpty()

internal val MediaItem.hasApiPosition: Boolean
    get() = requestMetadata.extras?.getBoolean(API_POSITION_KEY) == true
