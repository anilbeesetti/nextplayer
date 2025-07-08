package dev.anilbeesetti.nextplayer.feature.player.utils

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import timber.log.Timber

@UnstableApi
class SardineWebDavDataSource private constructor(
    private val sardine: Sardine,
    private val userAgent: String?,
) : BaseDataSource(true) {

    private var uri: Uri? = null
    private var inputStream: InputStream? = null
    private var opened = false
    private var bytesRemaining: Long = 0
    private var bytesRead: Long = 0

    companion object {
        private const val TAG = "SardineWebDavDataSource"
    }

    @UnstableApi
    class Factory(
        private val username: String? = null,
        private val password: String? = null,
        private val userAgent: String? = "NextPlayer/1.0",
    ) : DataSource.Factory {

        private val client by lazy {
            val builder = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)

            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                builder.addInterceptor { chain ->
                    val originalRequest = chain.request()
                    val newRequest = originalRequest.newBuilder()
                        .header("Authorization", okhttp3.Credentials.basic(username, password))
                        .header("User-Agent", userAgent ?: "NextPlayer/1.0")
                        .build()

                    val response = chain.proceed(newRequest)
                    Timber.tag(TAG).d("WebDAV request: ${newRequest.method} ${newRequest.url} - Response: ${response.code}")
                    response
                }
            }

            builder.build()
        }

        override fun createDataSource(): DataSource {
            val sardine = OkHttpSardine(client).apply {
                if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    setCredentials(username, password)
                }
            }

            return SardineWebDavDataSource(sardine, userAgent)
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            val url = dataSpec.uri.toString()

            Timber.tag(TAG).d("Opening WebDAV stream for: $url")

            if (dataSpec.position > 0) {
                val rangeHeader = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    "bytes=${dataSpec.position}-${dataSpec.position + dataSpec.length - 1}"
                } else {
                    "bytes=${dataSpec.position}-"
                }

                inputStream = sardine.get(url, mapOf("Range" to rangeHeader))
                Timber.tag(TAG).d("Using range request: $rangeHeader")
            } else {
                inputStream = sardine.get(url)
            }

            opened = true
            bytesRead = dataSpec.position

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                C.LENGTH_UNSET.toLong()
            }

            Timber.tag(TAG).d("WebDAV stream opened. Position: ${dataSpec.position}, Bytes remaining: $bytesRemaining")

            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open WebDAV stream: ${dataSpec.uri}")
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e as IOException,
                dataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        val inputStream = this.inputStream ?: throw IOException("Stream not opened")

        try {
            val readLength = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                minOf(length.toLong(), bytesRemaining).toInt()
            } else {
                length
            }

            val bytesRead = inputStream.read(buffer, offset, readLength)

            if (bytesRead == -1) {
                return C.RESULT_END_OF_INPUT
            }

            this.bytesRead += bytesRead
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesRead
            }

            bytesTransferred(bytesRead)
            return bytesRead
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read from WebDAV stream")
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e as IOException,
                DataSpec.Builder().setUri(uri!!).build(),
                HttpDataSource.HttpDataSourceException.TYPE_READ,
            )
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            inputStream?.close()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error closing WebDAV stream")
        } finally {
            inputStream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    fun setCredentials(username: String, password: String) {
        try {
            sardine.setCredentials(username, password)
            Timber.tag(TAG).d("WebDAV credentials updated")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set WebDAV credentials")
        }
    }
}
