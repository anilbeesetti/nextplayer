package com.graviton.feature.music.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun load(uriString: String?, path: String?): LyricsDocument = withContext(Dispatchers.IO) {
        val sidecar = path?.let { File(it) }?.takeIf { it.exists() }?.let { audio ->
            val lrc = File(audio.parentFile, audio.nameWithoutExtension + ".lrc")
            if (lrc.isFile) runCatching { lrc.readText() }.getOrNull() else null
        }
        val embedded = uriString?.let { readEmbedded(it) }
        LyricsParser.parse(sidecar ?: embedded)
    }

    private fun readEmbedded(uriString: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uriString))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LYRICS)
        } catch (_: Throwable) {
            null
        } finally {
            if (Build.VERSION.SDK_INT >= 29) retriever.close() else retriever.release()
        }
    }
}
