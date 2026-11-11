package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import dev.anilbeesetti.nextplayer.core.ui.R
import java.net.Inet4Address

@UnstableApi
internal class CastMediaItemConverter(
    private val context: Context,
    private val server: LocalCastServer,
) : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()
    private val originals = mutableMapOf<String, MediaItem>()

    // Media3 converts the whole queue on the main thread; look the address up once, not per item.
    private var host: String? = null

    override fun toMediaItem(item: MediaQueueItem): MediaItem {
        val converted = delegate.toMediaItem(item)
        return originals[key(converted)] ?: converted
    }

    override fun toMediaQueueItem(item: MediaItem): MediaQueueItem {
        val configuration = checkNotNull(item.localConfiguration)
        val uri = configuration.uri
        val mimeType = configuration.mimeType ?: mimeType(uri)
        val original = item.buildUpon().setMimeType(mimeType).build()
        val defaultInfo = checkNotNull(delegate.toMediaQueueItem(original).media)
        originals[key(item)] = item
        val url = if (uri.scheme in setOf(
                "http",
                "https",
            )
        ) {
            uri.toString()
        } else {
            try {
                require(
                    uri.scheme in setOf(
                        "content",
                        "file",
                    ),
                )
                server.register(
                    uri,
                    mimeType,
                    host ?: localAddress().also { host = it },
                )
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    R.string.cast_transfer_failed,
                    Toast.LENGTH_LONG,
                ).show()
                // An unreadable URI becomes a receiver playback error, never a sender crash.
                uri.toString()
            }
        }
        // Keep the original URI in customData, so transfer back never opens our HTTP URL.
        val info = MediaInfo.Builder(defaultInfo.contentId)
            .setContentUrl(url)
            .setContentType(mimeType)
            .setStreamType(defaultInfo.streamType)
            .setMetadata(defaultInfo.metadata)
            .setCustomData(defaultInfo.customData)
            .apply { item.mediaMetadata.durationMs?.let { setStreamDuration(it) } }
            .build()
        return MediaQueueItem.Builder(info).build()
    }

    fun clear() {
        originals.clear()
        host = null
    }

    private fun key(item: MediaItem): String = "${item.mediaId}:${item.localConfiguration?.uri}"

    private fun localAddress(): String {
        val connectivity = checkNotNull(context.getSystemService<ConnectivityManager>())
        return connectivity.allNetworks.asSequence()
            .filter { network ->
                val capabilities = connectivity.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true && (
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                    )
            }
            .flatMap { connectivity.getLinkProperties(it)?.linkAddresses.orEmpty() }
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress ?: error("No local network address")
    }

    private fun mimeType(uri: Uri): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
        uri.lastPathSegment?.substringAfterLast(
            '.',
            "",
        )?.lowercase(),
    ) ?: if (uri.scheme == "content") {
        runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "video/mp4"
    } else {
        "video/mp4"
    }
}
