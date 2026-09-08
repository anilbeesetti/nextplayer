package dev.anilbeesetti.nextplayer.core.media.network.datasource

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkConnectionResolver
import dev.anilbeesetti.nextplayer.core.media.network.NetworkUri
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A connected client and the path to read on it. */
class NetworkTarget(
    val client: NetworkClient,
    val filePath: String,
)

/**
 * Keeps the connected [NetworkClient] for the connection currently being played.
 *
 * Only one network item plays at a time, so switching connections disconnects the previous client
 * instead of accumulating them. A single client is shared by every [NetworkDataSource] opened for
 * that connection — the player opens a fresh source on each seek, and each one asks the client for
 * its own stream at an offset.
 */
@Singleton
class NetworkSessions @Inject constructor(
    private val resolver: NetworkConnectionResolver,
    private val clientFactory: NetworkClientFactory,
) {
    private val mutex = Mutex()
    private var connection: NetworkConnection? = null
    private var client: NetworkClient? = null

    /** Resolves [uri] to a connected client, reusing the current one when it already fits. */
    suspend fun target(uri: Uri): NetworkTarget = mutex.withLock {
        val target = resolve(uri)
        NetworkTarget(clientFor(target.connection), target.filePath)
    }

    /** Disconnects the active client. The cache stays usable — the next [target] reconnects. */
    suspend fun release() = mutex.withLock {
        client?.let(::disconnectDetached)
        connection = null
        client = null
    }

    private suspend fun resolve(uri: Uri): NetworkUri.Target {
        val connectionId = NetworkUri.connectionIdOf(uri)
            ?: return NetworkUri.adHocTargetOf(uri)
                ?: error("Not a playable network uri: $uri")

        val saved = resolver.connection(connectionId)
            ?: error("No saved network connection with id $connectionId")
        return NetworkUri.Target(saved, NetworkUri.filePathOf(uri, saved.protocol))
    }

    private suspend fun clientFor(target: NetworkConnection): NetworkClient {
        client?.takeIf { connection == target }?.let { existing ->
            if (!existing.isConnected()) existing.connect().getOrThrow()
            return existing
        }

        val fresh = clientFactory.create(target)
        fresh.connect().getOrThrow()

        client?.let(::disconnectDetached)
        connection = target
        client = fresh
        return fresh
    }

    private fun disconnectDetached(client: NetworkClient) {
        CoroutineScope(Dispatchers.IO).launch { runCatching { client.disconnect() } }
    }
}
