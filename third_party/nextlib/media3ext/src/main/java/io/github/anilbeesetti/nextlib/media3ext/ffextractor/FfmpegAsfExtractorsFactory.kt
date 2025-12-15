package io.github.anilbeesetti.nextlib.media3ext.ffextractor

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory

@UnstableApi
class FfmpegAsfExtractorsFactory(
    private val context: Context,
) : ExtractorsFactory {

    override fun createExtractors(): Array<Extractor> = arrayOf(
        FfmpegAsfExtractor(context = context, uri = null, responseHeaders = emptyMap()),
    )

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = arrayOf(
        FfmpegAsfExtractor(context = context, uri = uri, responseHeaders = responseHeaders),
    )
}

