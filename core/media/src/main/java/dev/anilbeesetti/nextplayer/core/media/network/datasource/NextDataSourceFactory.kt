package dev.anilbeesetti.nextplayer.core.media.network.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.media.network.NetworkUri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The player's data source factory: Media3's own [DefaultDataSource] for local and http(s) media,
 * and [NetworkDataSource] for the `smb`/`ftp`/`sftp`/`webdav` schemes it doesn't handle.
 */
@UnstableApi
@Singleton
class NextDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: NetworkSessions,
) : DataSource.Factory {

    override fun createDataSource(): DataSource = SchemeDispatchingDataSource(
        default = DefaultDataSource.Factory(context).createDataSource(),
        network = NetworkDataSource(sessions),
    )

    /** Disconnects the network client held for playback. Call when the player is released. */
    suspend fun release() = sessions.release()
}

/**
 * Picks the delegate on the first [open], since the scheme is only known then. Media3 asks the
 * factory for a source before it knows which item it will play.
 */
@UnstableApi
private class SchemeDispatchingDataSource(
    private val default: DataSource,
    private val network: DataSource,
) : DataSource {

    private var delegate: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val target = if (NetworkUri.isNetworkUri(dataSpec.uri)) network else default
        delegate = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(delegate) { "read() before open()" }.read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun close() {
        delegate?.close()
        delegate = null
    }

    override fun addTransferListener(transferListener: TransferListener) {
        default.addTransferListener(transferListener)
        network.addTransferListener(transferListener)
    }

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: emptyMap()
}
