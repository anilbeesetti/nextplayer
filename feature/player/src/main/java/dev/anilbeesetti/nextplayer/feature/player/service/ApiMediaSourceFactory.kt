package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dev.anilbeesetti.nextplayer.feature.player.extensions.ffmpegExtraction
import dev.anilbeesetti.nextplayer.feature.player.utils.apiHeaders
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegMediaSourceFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegSubtitleExtractorsFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.withFfmpegSubtitles

/** Creates independent sources so headers follow an item, including manifests, segments and subtitles. */
@UnstableApi
internal class ApiMediaSourceFactory(
    private val context: Context,
    private val dataSourceFactory: DataSource.Factory,
) : MediaSource.Factory by DefaultMediaSourceFactory(
    context,
).setDataSourceFactory(dataSourceFactory) {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val sources = RequestHeadersDataSourceFactory(
            dataSourceFactory,
            mediaItem.apiHeaders,
        )
        if (mediaItem.usesFfmpegExtractor) {
            return FfmpegMediaSourceFactory(sources).createMediaSource(mediaItem)
        }
        return DefaultMediaSourceFactory(
            context,
            FfmpegSubtitleExtractorsFactory(),
        )
            .setDataSourceFactory(sources)
            .withFfmpegSubtitles(sources)
            .createMediaSource(mediaItem)
    }
}

@UnstableApi
internal class RequestHeadersDataSourceFactory(
    private val delegate: DataSource.Factory,
    private val headers: Map<String, String>,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val source = delegate.createDataSource()
        return object : DataSource by source {
            override fun open(dataSpec: DataSpec): Long = source.open(
                if (dataSpec.uri.scheme in listOf(
                        "http",
                        "https",
                    )
                ) {
                    dataSpec.buildUpon().setHttpRequestHeaders(
                        headers + dataSpec.httpRequestHeaders,
                    ).build()
                } else {
                    dataSpec
                },
            )
        }
    }
}

@get:UnstableApi
internal val MediaItem.usesFfmpegExtractor: Boolean
    get() {
        val config = localConfiguration ?: return false
        return mediaMetadata.ffmpegExtraction && config.drmConfiguration == null &&
            Util.inferContentType(
                config.uri,
                config.mimeType,
            ) == C.CONTENT_TYPE_OTHER
    }
