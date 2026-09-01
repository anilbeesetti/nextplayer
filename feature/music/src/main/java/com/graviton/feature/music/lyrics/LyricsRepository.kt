package com.graviton.feature.music.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.model.LyricsSourceKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
) {
    private val providers: Map<LyricsSourceKind, LyricsProvider> by lazy {
        listOf(
            EmbeddedLyricsProvider(context),
            SidecarLyricsProvider(LyricsSourceKind.SIDECAR_LRC, "lrc"),
            SidecarLyricsProvider(LyricsSourceKind.SIDECAR_TTML, "ttml"),
            LrcLibLyricsProvider(),
        ).associateBy(LyricsProvider::kind)
    }
    private val cacheDir: File by lazy { File(context.cacheDir, "lyrics").apply { mkdirs() } }

    suspend fun load(uriString: String?, path: String?): LyricsDocument = load(
        LyricsRequest(
            mediaUri = uriString.orEmpty(),
            filePath = path,
            title = path?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty(),
            artist = "",
        ),
    )

    suspend fun load(request: LyricsRequest, allowRemote: Boolean = true): LyricsDocument = withContext(Dispatchers.IO) {
        if (request.mediaUri.isBlank()) return@withContext LyricsDocument(emptyList(), null)
        readCache(request)?.let { return@withContext LyricsParser.parse(it).copy(source = "cache") }
        val priority = preferencesRepository.applicationPreferences.value.musicLyricsProviderPriority
        for (kind in priority) {
            if (!allowRemote && kind == LyricsSourceKind.LRCLIB) continue
            val provider = providers[kind] ?: continue
            val best = runCatching { provider.find(request) }.getOrDefault(emptyList())
                .filter { candidate -> isSafeMatch(request, candidate) }
                .maxByOrNull(LyricsCandidate::confidence)
                ?: continue
            writeCache(request, best.rawLyrics)
            return@withContext LyricsParser.parse(best.rawLyrics).copy(source = kind.name)
        }
        LyricsDocument(emptyList(), null)
    }

    suspend fun search(request: LyricsRequest): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val remote = providers[LyricsSourceKind.LRCLIB] ?: return@withContext emptyList()
        runCatching { remote.search(request) }.getOrDefault(emptyList())
            .filter { isSafeMatch(request, it) || it.title.contains(request.title, ignoreCase = true) }
    }

    suspend fun select(request: LyricsRequest, candidate: LyricsCandidate): LyricsDocument = withContext(Dispatchers.IO) {
        writeCache(request, candidate.rawLyrics)
        LyricsParser.parse(candidate.rawLyrics).copy(source = candidate.provider.name)
    }

    suspend fun replace(request: LyricsRequest, raw: String): LyricsDocument = withContext(Dispatchers.IO) {
        writeCache(request, raw)
        LyricsParser.parse(raw).copy(source = "user")
    }

    suspend fun delete(request: LyricsRequest) = withContext(Dispatchers.IO) {
        cacheFile(request).delete()
    }

    private fun isSafeMatch(request: LyricsRequest, candidate: LyricsCandidate): Boolean {
        if (candidate.provider != LyricsSourceKind.LRCLIB) return true
        if (!candidate.title.equals(request.title, ignoreCase = true)) return false
        if (request.artist.isNotBlank() && !candidate.artist.equals(request.artist, ignoreCase = true)) return false
        val expected = request.durationMs
        val actual = candidate.durationMs
        return expected == null || actual == null || kotlin.math.abs(expected - actual) <= 5_000L
    }

    private fun cacheFile(request: LyricsRequest): File {
        val key = "${request.mediaUri}|${request.title}|${request.artist}|${request.durationMs}"
        val digest = messageDigest.get()!!.apply { reset() }.digest(key.toByteArray()).toHexString()
        return File(cacheDir, "$digest.txt")
    }

    private fun readCache(request: LyricsRequest): String? = cacheFile(request).takeIf(File::isFile)?.let { file ->
        runCatching { file.readText() }.getOrNull()
    }
    private fun writeCache(request: LyricsRequest, raw: String) {
        if (raw.isNotBlank()) runCatching { cacheFile(request).writeText(raw) }
    }

    companion object {
        private val messageDigest = object : ThreadLocal<MessageDigest>() {
            override fun initialValue(): MessageDigest = MessageDigest.getInstance("SHA-256")
        }

        private val HEX_CHARS = "0123456789abcdef".toCharArray()

        private fun ByteArray.toHexString(): String {
            val hexChars = CharArray(this.size * 2)
            for (j in this.indices) {
                val v = this[j].toInt() and 0xFF
                hexChars[j * 2] = HEX_CHARS[v ushr 4]
                hexChars[j * 2 + 1] = HEX_CHARS[v and 0x0F]
            }
            return String(hexChars)
        }
    }
}

private class SidecarLyricsProvider(
    override val kind: LyricsSourceKind,
    private val extension: String,
) : LyricsProvider {
    override suspend fun find(request: LyricsRequest): List<LyricsCandidate> {
        val audio = request.filePath?.let(::File)?.takeIf(File::exists) ?: return emptyList()
        val sidecar = File(audio.parentFile, "${audio.nameWithoutExtension}.$extension")
        val raw = sidecar.takeIf(File::isFile)?.let { file -> runCatching { file.readText() }.getOrNull() } ?: return emptyList()
        return listOf(LyricsCandidate(sidecar.absolutePath, kind, request.title, request.artist, request.album, request.durationMs, raw))
    }
}

private class EmbeddedLyricsProvider(private val context: Context) : LyricsProvider {
    override val kind = LyricsSourceKind.EMBEDDED

    override suspend fun find(request: LyricsRequest): List<LyricsCandidate> {
        val retriever = MediaMetadataRetriever()
        val raw = try {
            retriever.setDataSource(context, Uri.parse(request.mediaUri))
            // Android has no public lyrics key. A number of extractors expose the common USLT tag
            // through this vendor key; failure simply allows the next provider to run.
            retriever.extractMetadata(1000)
        } catch (_: RuntimeException) {
            null
        } finally {
            if (Build.VERSION.SDK_INT >= 29) retriever.close() else retriever.release()
        }
        return raw?.takeIf(String::isNotBlank)?.let {
            listOf(LyricsCandidate("embedded:${request.mediaUri}", kind, request.title, request.artist, rawLyrics = it))
        }.orEmpty()
    }
}

/** Minimal LRCLIB client; network errors are isolated and never escape the repository. */
private class LrcLibLyricsProvider : LyricsProvider {
    override val kind = LyricsSourceKind.LRCLIB
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun find(request: LyricsRequest): List<LyricsCandidate> {
        if (request.title.isBlank()) return emptyList()
        val params = buildList {
            add("track_name=${request.title.urlEncode()}")
            if (request.artist.isNotBlank()) add("artist_name=${request.artist.urlEncode()}")
            request.album?.takeIf(String::isNotBlank)?.let { add("album_name=${it.urlEncode()}") }
            request.durationMs?.takeIf { it > 0 }?.let { add("duration=${it / 1000}") }
        }.joinToString("&")
        val connection = URL("https://lrclib.net/api/get?$params").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("User-Agent", "Graviton/lyrics")
            if (connection.responseCode !in 200..299) return emptyList()
            val objectValue = connection.inputStream.bufferedReader().use { json.parseToJsonElement(it.readText()).jsonObject }
            val synced = objectValue["syncedLyrics"]?.jsonPrimitive?.contentOrNull
            val plain = objectValue["plainLyrics"]?.jsonPrimitive?.contentOrNull
            val raw = synced?.takeIf(String::isNotBlank) ?: plain?.takeIf(String::isNotBlank) ?: return emptyList()
            listOf(
                LyricsCandidate(
                    id = objectValue["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    provider = kind,
                    title = objectValue["trackName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    artist = objectValue["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    album = objectValue["albumName"]?.jsonPrimitive?.contentOrNull,
                    durationMs = objectValue["duration"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.times(1000)?.toLong(),
                    rawLyrics = raw,
                ),
            )
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun search(request: LyricsRequest): List<LyricsCandidate> {
        if (request.title.isBlank()) return emptyList()
        val query = buildList {
            add("track_name=${request.title.urlEncode()}")
            if (request.artist.isNotBlank()) add("artist_name=${request.artist.urlEncode()}")
        }.joinToString("&")
        val connection = URL("https://lrclib.net/api/search?$query").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("User-Agent", "Graviton/lyrics")
            if (connection.responseCode !in 200..299) return emptyList()
            val array = connection.inputStream.bufferedReader().use { json.parseToJsonElement(it.readText()).jsonArray }
            array.mapNotNull { element ->
                val value = element.jsonObject
                val raw = value["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf(String::isNotBlank)
                    ?: value["plainLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                LyricsCandidate(
                    id = value["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    provider = kind,
                    title = value["trackName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    artist = value["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    album = value["albumName"]?.jsonPrimitive?.contentOrNull,
                    durationMs = value["duration"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.times(1000)?.toLong(),
                    rawLyrics = raw,
                    confidence = 0.8f,
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
