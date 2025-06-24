package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import timber.log.Timber

@UnstableApi
class SmartDataSourceFactory(
    private val context: Context,
    private var webDavDataSourceFactory: SardineWebDavDataSource.Factory? = null,
) : DataSource.Factory {

    private val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(30000)
        .setReadTimeoutMs(30000)
        .setUserAgent("NextPlayer/1.0")

    private val defaultDataSourceFactory = DefaultDataSource.Factory(
        context,
        defaultHttpDataSourceFactory,
    )

    fun setWebDavDataSourceFactory(factory: SardineWebDavDataSource.Factory?) {
        this.webDavDataSourceFactory = factory
    }

    override fun createDataSource(): DataSource {
        return SmartDataSource(
            defaultDataSource = defaultDataSourceFactory.createDataSource(),
            webDavDataSourceFactory = webDavDataSourceFactory,
        )
    }
}

@UnstableApi
class SmartDataSource(
    private val defaultDataSource: DataSource,
    private val webDavDataSourceFactory: SardineWebDavDataSource.Factory?,
) : DataSource {

    companion object {
        private const val TAG = "SmartDataSource"
    }

    private var currentDataSource: DataSource? = null
    private var transferListeners = mutableListOf<androidx.media3.datasource.TransferListener>()

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        transferListeners.add(transferListener)
        defaultDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        val uri = dataSpec.uri

        // Check if it's a WebDAV URL and there's a configured WebDAV data source factory
        if (isWebDavUrl(uri) && webDavDataSourceFactory != null) {
            Timber.tag(TAG).d("Using WebDAV data source for: $uri")
            try {
                val webDavDataSource = webDavDataSourceFactory.createDataSource()
                // Add registered transfer listeners
                transferListeners.forEach { listener ->
                    webDavDataSource.addTransferListener(listener)
                }
                currentDataSource = webDavDataSource
                return webDavDataSource.open(dataSpec)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to open WebDAV data source, falling back to default")
                // Fall back to default data source on failure
            }
        }

        Timber.tag(TAG).d("Using default data source for: $uri")
        currentDataSource = defaultDataSource
        return defaultDataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return currentDataSource?.read(buffer, offset, length) ?: defaultDataSource.read(buffer, offset, length)
    }

    override fun getUri(): Uri? {
        return currentDataSource?.uri ?: defaultDataSource.uri
    }

    override fun close() {
        currentDataSource?.close() ?: defaultDataSource.close()
        currentDataSource = null
    }

    private fun isWebDavUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val path = uri.path?.lowercase() ?: ""

        // Try to use WebDAV as long as WebDAV data source factory is available
        // This is because the user explicitly set WebDAV credentials, indicating they want to use WebDAV
        if (webDavDataSourceFactory != null && (scheme == "http" || scheme == "https")) {
            Timber.tag(TAG).d("WebDAV factory available, trying WebDAV for: $uri")
            return true
        }

        return false
    }

    private fun isLikelyWebDav(uri: Uri): Boolean {
        // More heuristic rules can be added here to determine if it's WebDAV
        // For example, check URI patterns, hostnames, etc.
        val path = uri.path?.lowercase() ?: ""
        return path.contains("webdav") ||
            path.contains("/dav") ||
            path.contains("/remote.php") ||
            path.contains("/files")
    }
}
