package dev.anilbeesetti.nextplayer.feature.player.ffmpeg

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * A DataSource that immediately reports end-of-input and performs no I/O.
 *
 * Used so WMV/ASF playback can be driven by FFmpeg demuxing (inside the extractor) without
 * creating a second HTTP connection via Media3's DataSource.
 */
internal class NoOpDataSource : DataSource {

    override fun addTransferListener(transferListener: TransferListener) = Unit

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long = C_LENGTH_UNSET

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C_RESULT_END_OF_INPUT

    override fun getUri(): Uri? = null

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    @Throws(IOException::class)
    override fun close() = Unit

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = NoOpDataSource()
    }

    private companion object {
        private const val C_RESULT_END_OF_INPUT = -1
        private const val C_LENGTH_UNSET = -1L
    }
}
