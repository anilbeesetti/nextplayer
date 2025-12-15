package dev.anilbeesetti.nextplayer.feature.player.ffmpeg

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.Locale

internal class ConditionalDataSourceFactory(
    private val context: Context,
    private val shouldUseNoOpDataSource: (Uri) -> Boolean,
    private val realFactory: DataSource.Factory = DefaultDataSource.Factory(context),
    private val noOpFactory: DataSource.Factory = NoOpDataSource.Factory(),
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        ConditionalDataSource(
            context = context,
            shouldUseNoOpDataSource = shouldUseNoOpDataSource,
            realFactory = realFactory,
            noOpFactory = noOpFactory,
        )
}

private class ConditionalDataSource(
    private val context: Context,
    private val shouldUseNoOpDataSource: (Uri) -> Boolean,
    private val realFactory: DataSource.Factory,
    private val noOpFactory: DataSource.Factory,
) : DataSource {

    private var delegate: DataSource? = null
    private val transferListeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        delegate?.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val useNoOp = shouldUseNoOpDataSource(uri)
        delegate = (if (useNoOp) noOpFactory else realFactory).createDataSource()
        transferListeners.forEach { delegate!!.addTransferListener(it) }
        return delegate!!.open(dataSpec)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: C_RESULT_END_OF_INPUT

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    @Throws(IOException::class)
    override fun close() {
        delegate?.close()
        delegate = null
    }

    companion object {
        private const val C_RESULT_END_OF_INPUT = -1
    }
}

internal object WmvAsfDetector {
    fun isWmvAsf(context: Context, uri: Uri): Boolean {
        val scheme = (uri.scheme ?: "").lowercase(Locale.US)
        if (scheme == "http" || scheme == "https") return false

        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase(Locale.US).orEmpty()
        if (
            mimeType.contains("video/x-ms-wmv") ||
            mimeType.contains("video/x-ms-asf") ||
            mimeType.contains("application/vnd.ms-asf") ||
            mimeType.contains("audio/x-ms-wma")
        ) {
            return true
        }

        val name = getDisplayName(context, uri).lowercase(Locale.US)
        val last = (uri.lastPathSegment ?: "").lowercase(Locale.US)

        return name.endsWith(".wmv") ||
            name.endsWith(".asf") ||
            name.endsWith(".wma") ||
            last.endsWith(".wmv") ||
            last.endsWith(".asf") ||
            last.endsWith(".wma")
    }

    private fun getDisplayName(context: Context, uri: Uri): String {
        if (!uri.scheme.equals("content", ignoreCase = true)) return uri.lastPathSegment ?: ""
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }.getOrNull() ?: (uri.lastPathSegment ?: "")
    }
}
