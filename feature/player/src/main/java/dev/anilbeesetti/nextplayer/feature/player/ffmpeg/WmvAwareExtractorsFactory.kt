package dev.anilbeesetti.nextplayer.feature.player.ffmpeg

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import io.github.anilbeesetti.nextlib.media3ext.ffextractor.FfmpegAsfExtractorsFactory

@UnstableApi
internal class WmvAwareExtractorsFactory(
    context: Context,
) : ExtractorsFactory {
    private val ffmpegFactory = FfmpegAsfExtractorsFactory(context)
    private val defaultFactory = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> =
        ffmpegFactory.createExtractors() + defaultFactory.createExtractors()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        ffmpegFactory.createExtractors(uri, responseHeaders) + defaultFactory.createExtractors(uri, responseHeaders)
}
