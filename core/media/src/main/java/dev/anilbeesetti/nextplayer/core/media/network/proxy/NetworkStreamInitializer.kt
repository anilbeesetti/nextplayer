package dev.anilbeesetti.nextplayer.core.media.network.proxy

import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes the shared-client setup required before a response opens its own file stream. */
internal class NetworkStreamInitializer(
    private val client: NetworkClient,
    private val filePath: String,
) {
    private val mutex = Mutex()
    private var fileSize: Long? = null

    suspend fun initialize(): Long = mutex.withLock {
        if (!client.isConnected()) client.connect().getOrThrow()
        fileSize ?: client.fileSize(filePath).also { fileSize = it }
    }
}
