package dev.anilbeesetti.nextplayer.core.media.network.datasource

import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkConnectionResolver
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A connected client together with the connection it was built from. */
class NetworkSession(
    val connection: NetworkConnection,
    val client: NetworkClient,
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
    private var current: NetworkSession? = null

    suspend fun session(connectionId: Long): NetworkSession = mutex.withLock {
        current?.takeIf { it.connection.id == connectionId }?.let { session ->
            if (!session.client.isConnected()) session.client.connect().getOrThrow()
            return@withLock session
        }

        val connection = resolver.connection(connectionId)
            ?: error("No saved network connection with id $connectionId")
        val client = clientFactory.create(connection)
        client.connect().getOrThrow()

        val previous = current
        current = NetworkSession(connection, client)
        previous?.let { disconnectDetached(it.client) }
        current!!
    }

    /** Disconnects the active client. The cache stays usable — the next [session] reconnects. */
    suspend fun release() = mutex.withLock {
        current?.let { disconnectDetached(it.client) }
        current = null
    }

    private fun disconnectDetached(client: NetworkClient) {
        CoroutineScope(Dispatchers.IO).launch { runCatching { client.disconnect() } }
    }
}
