package dev.anilbeesetti.nextplayer.core.media.network

import dev.anilbeesetti.nextplayer.core.model.NetworkConnection

/**
 * Looks up a saved connection by id.
 *
 * Declared here rather than taking a repository dependency because `core:data` already depends on
 * `core:media`; the binding lives in `core:data`, next to the repository that satisfies it.
 */
fun interface NetworkConnectionResolver {
    suspend fun connection(id: Long): NetworkConnection?
}
