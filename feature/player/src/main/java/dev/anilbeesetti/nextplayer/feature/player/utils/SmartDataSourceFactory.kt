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
    private var webDavDataSourceFactory: SardineWebDavDataSource.Factory? = null
) : DataSource.Factory {

    private val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(30000)
        .setReadTimeoutMs(30000)
        .setUserAgent("NextPlayer/1.0")

    private val defaultDataSourceFactory = DefaultDataSource.Factory(
        context,
        defaultHttpDataSourceFactory
    )

    fun setWebDavDataSourceFactory(factory: SardineWebDavDataSource.Factory?) {
        this.webDavDataSourceFactory = factory
    }

    override fun createDataSource(): DataSource {
        return SmartDataSource(
            defaultDataSource = defaultDataSourceFactory.createDataSource(),
            webDavDataSourceFactory = webDavDataSourceFactory
        )
    }
}

@UnstableApi
class SmartDataSource(
    private val defaultDataSource: DataSource,
    private val webDavDataSourceFactory: SardineWebDavDataSource.Factory?
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
        
        // 检查是否是WebDAV URL并且有配置的WebDAV数据源工厂
        if (isWebDavUrl(uri) && webDavDataSourceFactory != null) {
            Timber.tag(TAG).d("Using WebDAV data source for: $uri")
            try {
                val webDavDataSource = webDavDataSourceFactory.createDataSource()
                // 添加已注册的传输监听器
                transferListeners.forEach { listener ->
                    webDavDataSource.addTransferListener(listener)
                }
                currentDataSource = webDavDataSource
                return webDavDataSource.open(dataSpec)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to open WebDAV data source, falling back to default")
                // 失败时回退到默认数据源
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
        
        // 只要有WebDAV数据源工厂可用，就尝试使用它
        // 这是因为用户明确设置了WebDAV凭据，说明他们想要使用WebDAV
        if (webDavDataSourceFactory != null && (scheme == "http" || scheme == "https")) {
            Timber.tag(TAG).d("WebDAV factory available, trying WebDAV for: $uri")
            return true
        }
        
        return false
    }
    
    private fun isLikelyWebDav(uri: Uri): Boolean {
        // 这里可以添加更多启发式规则来判断是否是WebDAV
        // 例如检查URI模式、主机名等
        val path = uri.path?.lowercase() ?: ""
        return path.contains("webdav") || 
               path.contains("/dav") ||
               path.contains("/remote.php") ||
               path.contains("/files")
    }
}
