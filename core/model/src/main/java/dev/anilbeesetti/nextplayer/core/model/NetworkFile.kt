package dev.anilbeesetti.nextplayer.core.model

/**
 * A file or directory entry on a network location.
 *
 * [path] is opaque to callers other than the client that produced it — it is fed back into
 * `NetworkClient.listFiles`/`openStream` to navigate into folders or open videos.
 */
data class NetworkFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    /** Last-modified time in epoch millis, or null if the server did not report it. */
    val modified: Long? = null,
)
